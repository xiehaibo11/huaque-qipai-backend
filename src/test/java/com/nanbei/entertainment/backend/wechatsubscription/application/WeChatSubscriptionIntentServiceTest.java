package com.nanbei.entertainment.backend.wechatsubscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantStatus;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionGrantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeChatSubscriptionIntentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String SUBJECT = "appid:wx-test:openid:openid-1";

    @Mock WeChatSubscriptionGrantRepository repository;
    @Mock WeChatSubscriptionIdentityService identityService;

    private CryptoService cryptoService;
    private WeChatSubscriptionIntentService service;

    @BeforeEach
    void setUp() {
        cryptoService = spy(new CryptoService());
        service =
                new WeChatSubscriptionIntentService(
                        repository,
                        identityService,
                        cryptoService,
                        new WeChatSubscriptionProperties(
                                true,
                                WeChatSubscriptionProperties.TEMPLATE_ID,
                                1000),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createStoresOnlyHashesAndReturnsSdkChallenge() {
        doReturn("raw-_token").when(cryptoService).randomToken();
        when(identityService.requireCurrent(USER_ID))
                .thenReturn(
                        new WeChatSubscriptionIdentity(
                                SUBJECT,
                                "openid-1",
                                cryptoService.sha256(SUBJECT)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<WeChatSubscriptionGrantEntity> saved =
                ArgumentCaptor.forClass(WeChatSubscriptionGrantEntity.class);

        WeChatSubscriptionIntentResponse response = service.create(USER_ID);

        verify(repository).save(saved.capture());
        assertThat(response.templateId())
                .isEqualTo(WeChatSubscriptionProperties.TEMPLATE_ID);
        assertThat(response.scene()).isEqualTo(1000);
        assertThat(response.reserved()).isNotBlank();
        assertThat(response.reserved()).matches("[A-Za-z0-9]{1,128}");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(saved.getValue().getReservedHash())
                .isEqualTo(cryptoService.sha256(response.reserved()));
        assertThat(saved.getValue().getOpenIdSubjectHash())
                .isEqualTo(cryptoService.sha256(SUBJECT));
    }

    @Test
    void completeAcceptsExactConfirmationOnlyOnce() {
        stubConfirmationIdentity();
        WeChatSubscriptionGrantEntity grant = pendingGrant(NOW.plusSeconds(600));
        when(repository.findLockedById(grant.getId())).thenReturn(Optional.of(grant));
        WeChatSubscriptionCompletion completion =
                confirmation(grant, "confirm", 0, "reserved", "openid-1");

        WeChatSubscriptionCompleteResponse first =
                service.complete(USER_ID, grant.getId(), completion);
        WeChatSubscriptionCompleteResponse replay =
                service.complete(USER_ID, grant.getId(), completion);

        assertThat(first.status()).isEqualTo(WeChatSubscriptionGrantStatus.AVAILABLE);
        assertThat(replay.status()).isEqualTo(WeChatSubscriptionGrantStatus.AVAILABLE);
        assertThat(grant.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void cancelAndProviderRejectionAllowMissingOpenIdAndNeverCreateAvailableGrant() {
        WeChatSubscriptionGrantEntity cancelled = pendingGrant(NOW.plusSeconds(600));
        when(repository.findLockedById(cancelled.getId()))
                .thenReturn(Optional.of(cancelled));

        service.complete(
                USER_ID,
                cancelled.getId(),
                confirmation(cancelled, "cancel", -2, "reserved", null));

        assertThat(cancelled.getStatus())
                .isEqualTo(WeChatSubscriptionGrantStatus.CANCELLED);

        WeChatSubscriptionGrantEntity denied = pendingGrant(NOW.plusSeconds(600));
        when(repository.findLockedById(denied.getId())).thenReturn(Optional.of(denied));
        service.complete(
                USER_ID,
                denied.getId(),
                confirmation(denied, "confirm", -4, "reserved", null));

        assertThat(denied.getStatus()).isEqualTo(WeChatSubscriptionGrantStatus.DENIED);
    }

    @Test
    void completeRejectsTamperingAndExpiry() {
        stubConfirmationIdentity();
        WeChatSubscriptionGrantEntity tampered = pendingGrant(NOW.plusSeconds(600));
        when(repository.findLockedById(tampered.getId()))
                .thenReturn(Optional.of(tampered));

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        USER_ID,
                                        tampered.getId(),
                                        confirmation(
                                                tampered,
                                                "confirm",
                                                0,
                                                "changed",
                                                "openid-1")))
                .isInstanceOf(ApiException.class);
        assertThat(tampered.getStatus())
                .isEqualTo(WeChatSubscriptionGrantStatus.PENDING);

        WeChatSubscriptionGrantEntity expired = pendingGrant(NOW);
        when(repository.findLockedById(expired.getId())).thenReturn(Optional.of(expired));
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        USER_ID,
                                        expired.getId(),
                                        confirmation(
                                                expired,
                                                "confirm",
                                                0,
                                                "reserved",
                                                "openid-1")))
                .isInstanceOf(ApiException.class);
        assertThat(expired.getStatus())
                .isEqualTo(WeChatSubscriptionGrantStatus.EXPIRED);
    }

    @Test
    void completeBindsTransactionToIntentAndRequiresOpenIdForSuccessfulConfirm() {
        WeChatSubscriptionGrantEntity grant = pendingGrant(NOW.plusSeconds(600));
        when(repository.findLockedById(grant.getId())).thenReturn(Optional.of(grant));

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        USER_ID,
                                        grant.getId(),
                                        new WeChatSubscriptionCompletion(
                                                0,
                                                "confirm",
                                                WeChatSubscriptionProperties.TEMPLATE_ID,
                                                1000,
                                                "reserved",
                                                "openid-1",
                                                "another-intent")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        USER_ID,
                                        grant.getId(),
                                        confirmation(
                                                grant,
                                                "confirm",
                                                0,
                                                "reserved",
                                                null)))
                .isInstanceOf(ApiException.class);
        assertThat(grant.getStatus()).isEqualTo(WeChatSubscriptionGrantStatus.PENDING);
    }

    @Test
    void replayStillRejectsTamperedPayloadBeforeReturningTerminalStatus() {
        stubConfirmationIdentity();
        WeChatSubscriptionGrantEntity grant = pendingGrant(NOW.plusSeconds(600));
        when(repository.findLockedById(grant.getId())).thenReturn(Optional.of(grant));
        service.complete(
                USER_ID,
                grant.getId(),
                confirmation(grant, "confirm", 0, "reserved", "openid-1"));

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        USER_ID,
                                        grant.getId(),
                                        confirmation(
                                                grant,
                                                "confirm",
                                                0,
                                                "changed",
                                                "openid-1")))
                .isInstanceOf(ApiException.class);
    }

    private WeChatSubscriptionGrantEntity pendingGrant(Instant expiresAt) {
        return new WeChatSubscriptionGrantEntity(
                USER_ID,
                WeChatSubscriptionProperties.TEMPLATE_ID,
                1000,
                cryptoService.sha256("reserved"),
                cryptoService.sha256(SUBJECT),
                expiresAt,
                NOW.minusSeconds(1));
    }

    private void stubConfirmationIdentity() {
        when(identityService.subjectHash("openid-1"))
                .thenReturn(cryptoService.sha256(SUBJECT));
    }

    private static WeChatSubscriptionCompletion confirmation(
            WeChatSubscriptionGrantEntity grant,
            String action,
            int errCode,
            String reserved,
            String openId) {
        return new WeChatSubscriptionCompletion(
                errCode,
                action,
                WeChatSubscriptionProperties.TEMPLATE_ID,
                1000,
                reserved,
                openId,
                grant.getId().toString());
    }
}
