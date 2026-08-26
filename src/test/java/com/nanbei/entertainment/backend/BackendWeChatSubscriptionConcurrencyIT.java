package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionCompletion;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionEnqueueService;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionIntentResponse;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionIntentService;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionDeliveryEntity;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "nanbei.wechat.enabled=true",
            "nanbei.wechat.app-id=wx-test",
            "nanbei.wechat.app-secret=not-used-by-this-test",
            "nanbei.wechat.subscription.enabled=true",
            "nanbei.wechat.subscription.template-id="
                    + WeChatSubscriptionProperties.TEMPLATE_ID,
            "nanbei.wechat.subscription.scene=1000",
            "nanbei.wechat.subscription.worker-initial-delay=PT1H"
        })
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendWeChatSubscriptionConcurrencyIT {
    @Autowired WeChatSubscriptionIntentService intentService;
    @Autowired WeChatSubscriptionEnqueueService enqueueService;
    @Autowired UserRepository userRepository;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void aliasBoundGrantAndBusinessEventStaySingleUnderConcurrency()
            throws Exception {
        UserEntity user = userRepository.save(UserEntity.create("微信订阅并发测试"));
        UUID userId = user.getId();
        try {
            assertThatThrownBy(() -> intentService.create(userId))
                    .isInstanceOf(ApiException.class);
            identityRepository.save(
                    new UserIdentityEntity(
                            user,
                            IdentityProvider.WECHAT,
                            "appid:wx-test:openid:openid-concurrency",
                            null));

            WeChatSubscriptionIntentResponse intent = intentService.create(userId);
            String storedHash =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT reserved_hash
                            FROM wechat_subscription_grants
                            WHERE id = ?
                            """,
                            String.class,
                            intent.intentId());
            assertThat(storedHash).hasSize(64).isNotEqualTo(intent.reserved());

            WeChatSubscriptionCompletion completion =
                    new WeChatSubscriptionCompletion(
                            0,
                            "confirm",
                            WeChatSubscriptionProperties.TEMPLATE_ID,
                            1000,
                            intent.reserved(),
                            "openid-concurrency",
                            intent.intentId().toString());
            ConcurrentTestRequests.run(
                    2,
                    () -> intentService.complete(userId, intent.intentId(), completion),
                    () -> {},
                    Duration.ofSeconds(10));

            List<WeChatSubscriptionDeliveryEntity> deliveries =
                    ConcurrentTestRequests.run(
                            2,
                            () ->
                                    enqueueService.enqueue(
                                            userId,
                                            "REAL_SYSTEM_EVENT",
                                            "event-concurrency",
                                            "系统通知",
                                            "真实业务事件已完成",
                                            null),
                            () -> {},
                            Duration.ofSeconds(10));

            assertThat(deliveries.stream()
                            .map(WeChatSubscriptionDeliveryEntity::getId)
                            .distinct())
                    .hasSize(1);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    """
                                    SELECT count(*)
                                    FROM wechat_subscription_grants
                                    WHERE user_id = ? AND status = 'CLAIMED'
                                    """,
                                    Long.class,
                                    userId))
                    .isEqualTo(1L);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    """
                                    SELECT count(*)
                                    FROM wechat_subscription_deliveries
                                    WHERE user_id = ?
                                      AND event_type = 'REAL_SYSTEM_EVENT'
                                      AND event_id = 'event-concurrency'
                                    """,
                                    Long.class,
                                    userId))
                    .isEqualTo(1L);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM wechat_subscription_deliveries WHERE user_id = ?",
                    userId);
            jdbcTemplate.update(
                    "DELETE FROM wechat_subscription_grants WHERE user_id = ?",
                    userId);
            jdbcTemplate.update("DELETE FROM user_identities WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
    }
}
