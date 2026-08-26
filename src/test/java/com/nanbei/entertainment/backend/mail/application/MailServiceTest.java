package com.nanbei.entertainment.backend.mail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.mail.domain.MailEntity;
import com.nanbei.entertainment.backend.mail.infrastructure.MailRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {
    private static final Instant SEND_AT = Instant.parse("2026-08-07T10:00:00Z");

    @Mock MailRepository mailRepository;
    @Mock PlayerWalletRepository walletRepository;

    MailService service;

    @BeforeEach
    void setUp() {
        service = new MailService(mailRepository, walletRepository, new ObjectMapper());
    }

    @Test
    void summaryIsZeroForEmptyMailbox() {
        UUID userId = UUID.randomUUID();
        when(mailRepository.findVisible(eq(userId), any())).thenReturn(List.of());

        MailSummaryResponse summary = service.summary(userId);

        assertThat(summary.unreadCount()).isZero();
        assertThat(summary.awardCount()).isZero();
    }

    @Test
    void summaryCountsUnreadAndUnclaimedAwardMails() {
        UUID userId = UUID.randomUUID();
        MailEntity plainUnread = mail(1L, userId, "[]", false, false);
        MailEntity awardUnread = mail(2L, userId, attachments("COIN", 100), false, false);
        MailEntity readClaimed = mail(3L, userId, attachments("COIN", 100), true, true);
        when(mailRepository.findVisible(eq(userId), any()))
                .thenReturn(List.of(plainUnread, awardUnread, readClaimed));

        MailSummaryResponse summary = service.summary(userId);

        assertThat(summary.unreadCount()).isEqualTo(2);
        assertThat(summary.awardCount()).isEqualTo(1);
    }

    @Test
    void listsEmptyMailboxAsEmptyArray() {
        UUID userId = UUID.randomUUID();
        when(mailRepository.findVisible(eq(userId), any())).thenReturn(List.of());

        assertThat(service.list(userId).mails()).isEmpty();
    }

    @Test
    void detailMarksUnreadMailAsRead() {
        UUID userId = UUID.randomUUID();
        MailEntity mail = mail(9L, userId, attachments("DIAMOND", 20), false, false);
        when(mailRepository.findByIdAndUserId(9L, userId)).thenReturn(Optional.of(mail));

        MailDetailResponse detail = service.detail(userId, 9L);

        assertThat(detail.mailId()).isEqualTo(9L);
        assertThat(detail.read()).isTrue();
        assertThat(detail.claimed()).isFalse();
        assertThat(mail.getReadAt()).isNotNull();
        assertThat(detail.attachments()).hasSize(1);
        assertThat(detail.attachments().get(0).rewardType()).isEqualTo("DIAMOND");
        assertThat(detail.attachments().get(0).amount()).isEqualTo(20);
    }

    @Test
    void detailKeepsReadTimestampOnRepeatView() {
        UUID userId = UUID.randomUUID();
        MailEntity mail = mail(9L, userId, "[]", false, false);
        when(mailRepository.findByIdAndUserId(9L, userId)).thenReturn(Optional.of(mail));

        service.detail(userId, 9L);
        Instant firstReadAt = mail.getReadAt();
        MailDetailResponse second = service.detail(userId, 9L);

        assertThat(second.read()).isTrue();
        assertThat(mail.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    void detailOfAnotherUsersMailFailsWithStableError() {
        UUID userId = UUID.randomUUID();
        when(mailRepository.findByIdAndUserId(9L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(userId, 9L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MAIL_NOT_FOUND);
    }

    @Test
    void detailOfDeletedMailFailsWithStableError() {
        UUID userId = UUID.randomUUID();
        MailEntity mail = mail(9L, userId, "[]", true, false);
        mail.markDeleted(Instant.now());
        when(mailRepository.findByIdAndUserId(9L, userId)).thenReturn(Optional.of(mail));

        assertThatThrownBy(() -> service.detail(userId, 9L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MAIL_NOT_FOUND);
    }

    @Test
    void readAllReturnsMarkedCount() {
        UUID userId = UUID.randomUUID();
        when(mailRepository.markAllRead(eq(userId), any())).thenReturn(3);

        assertThat(service.readAll(userId).markedCount()).isEqualTo(3);
    }

    @Test
    void deleteSkipsMailsWithUnclaimedAttachments() {
        UUID userId = UUID.randomUUID();
        MailEntity plain = mail(1L, userId, "[]", true, false);
        MailEntity withAward = mail(2L, userId, attachments("COIN", 100), true, false);
        MailEntity claimedAward = mail(3L, userId, attachments("COIN", 100), true, true);
        when(mailRepository.findByUserIdAndIdIn(userId, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(plain, withAward, claimedAward));

        MailDeletedCountResponse result = service.delete(userId, List.of(1L, 2L, 3L));

        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(result.deletedMailIds()).containsExactly(1L, 3L);
        assertThat(plain.getDeletedAt()).isNotNull();
        assertThat(withAward.getDeletedAt()).isNull();
        assertThat(claimedAward.getDeletedAt()).isNotNull();
    }

    @Test
    void claimCreditsWalletAndAggregatesRewards() {
        UUID userId = UUID.randomUUID();
        MailEntity first = mail(1L, userId, attachments("COIN", 100), true, false);
        MailEntity second = mail(2L, userId, twoRewards(), true, false);
        when(mailRepository.findLockedByUserIdAndIdIn(userId, List.of(1L, 2L)))
                .thenReturn(List.of(first, second));
        PlayerWalletEntity wallet = new PlayerWalletEntity(userId, 0, 0, 0, 0);
        when(walletRepository.findLockedByUserId(userId)).thenReturn(Optional.of(wallet));

        MailClaimResponse result = service.claim(userId, List.of(1L, 2L));

        assertThat(result.claimedMailIds()).containsExactly(1L, 2L);
        assertThat(result.rewards())
                .containsExactly(
                        new MailClaimReward("COIN", 150),
                        new MailClaimReward("DIAMOND", 5),
                        new MailClaimReward("ROOM_CARD", 2));
        assertThat(result.wallet().coins()).isEqualTo(150);
        assertThat(result.wallet().diamonds()).isEqualTo(5);
        assertThat(result.wallet().roomCards()).isEqualTo(2);
        assertThat(first.getClaimedAt()).isNotNull();
        assertThat(first.getReadAt()).isNotNull();
        assertThat(second.getReadAt()).isNotNull();
        verify(walletRepository).save(wallet);
    }

    @Test
    void claimSkipsAlreadyClaimedMailsIdempotently() {
        UUID userId = UUID.randomUUID();
        MailEntity claimed = mail(1L, userId, attachments("COIN", 100), true, true);
        when(mailRepository.findLockedByUserIdAndIdIn(userId, List.of(1L)))
                .thenReturn(List.of(claimed));
        PlayerWalletEntity wallet = new PlayerWalletEntity(userId, 0, 0, 100, 0);
        when(walletRepository.findById(userId)).thenReturn(Optional.of(wallet));

        MailClaimResponse result = service.claim(userId, List.of(1L));

        assertThat(result.claimedMailIds()).isEmpty();
        assertThat(result.rewards()).isEmpty();
        assertThat(result.wallet().coins()).isEqualTo(100);
        verify(walletRepository, never()).findLockedByUserId(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void claimSkipsExpiredAndForeignMails() {
        UUID userId = UUID.randomUUID();
        MailEntity expired = mail(1L, userId, attachments("COIN", 100), true, false);
        ReflectionTestUtils.setField(expired, "expireAt", Instant.parse("2020-01-01T00:00:00Z"));
        when(mailRepository.findLockedByUserIdAndIdIn(userId, List.of(1L, 99L)))
                .thenReturn(List.of(expired));
        when(walletRepository.findById(userId)).thenReturn(Optional.empty());

        MailClaimResponse result = service.claim(userId, List.of(1L, 99L));

        assertThat(result.claimedMailIds()).isEmpty();
        assertThat(result.wallet().coins()).isZero();
        assertThat(expired.getClaimedAt()).isNull();
        verify(walletRepository, never()).save(any());
    }

    private static MailEntity mail(
            long id, UUID userId, String attachments, boolean read, boolean claimed) {
        MailEntity mail =
                new MailEntity(
                        userId,
                        "标题" + id,
                        "简介",
                        "正文",
                        "系统",
                        attachments,
                        SEND_AT,
                        null);
        ReflectionTestUtils.setField(mail, "id", id);
        if (read) {
            mail.markRead(SEND_AT.plusSeconds(60));
        }
        if (claimed) {
            mail.markClaimed(SEND_AT.plusSeconds(120));
        }
        return mail;
    }

    private static String attachments(String rewardType, long amount) {
        return "[{\"icon\":\"mail_reward\",\"rewardType\":\""
                + rewardType
                + "\",\"amount\":"
                + amount
                + ",\"description\":\"奖励\"}]";
    }

    private static String twoRewards() {
        return "[{\"icon\":\"a\",\"rewardType\":\"COIN\",\"amount\":50,\"description\":\"金币\"},"
                + "{\"icon\":\"b\",\"rewardType\":\"DIAMOND\",\"amount\":5,\"description\":\"钻石\"},"
                + "{\"icon\":\"c\",\"rewardType\":\"ROOM_CARD\",\"amount\":2,\"description\":\"房卡\"}]";
    }
}
