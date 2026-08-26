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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MailOriginalPolicyTest {
    private static final Instant SEND_AT = Instant.parse("2026-08-07T10:00:00Z");

    @Mock MailRepository mailRepository;
    @Mock PlayerWalletRepository walletRepository;

    MailService service;

    @BeforeEach
    void setUp() {
        service = new MailService(mailRepository, walletRepository, new ObjectMapper());
    }

    @Test
    void listsTenMailsPerOriginalPageAndReportsWhetherMoreExist() {
        UUID userId = UUID.randomUUID();
        List<MailEntity> eleven =
                java.util.stream.LongStream.rangeClosed(1, 11)
                        .mapToObj(id -> mail(id, userId, true))
                        .toList();
        when(mailRepository.findVisible(eq(userId), any(), any(Pageable.class)))
                .thenReturn(eleven);

        MailListResponse page = service.list(userId, 2);

        assertThat(page.mails()).hasSize(10);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void detailOfExpiredMailFailsWithStableError() {
        UUID userId = UUID.randomUUID();
        MailEntity mail = mail(9L, userId, true);
        ReflectionTestUtils.setField(mail, "expireAt", Instant.parse("2020-01-01T00:00:00Z"));
        when(mailRepository.findByIdAndUserId(9L, userId)).thenReturn(Optional.of(mail));

        assertThatThrownBy(() -> service.detail(userId, 9L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MAIL_NOT_FOUND);
    }

    @Test
    void deleteSkipsUnreadMailLikeTheOriginalClientProtocol() {
        UUID userId = UUID.randomUUID();
        MailEntity unread = mail(1L, userId, false);
        when(mailRepository.findByUserIdAndIdIn(userId, List.of(1L)))
                .thenReturn(List.of(unread));

        MailDeletedCountResponse result = service.delete(userId, List.of(1L));

        assertThat(result.deletedCount()).isZero();
        assertThat(unread.getDeletedAt()).isNull();
    }

    @Test
    void claimRejectsMoreThanTheOriginalTenMailBatchLimit() {
        UUID userId = UUID.randomUUID();
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);

        assertThatThrownBy(() -> service.claim(userId, ids))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(mailRepository, never()).findLockedByUserIdAndIdIn(any(), any());
    }

    private static MailEntity mail(long id, UUID userId, boolean read) {
        MailEntity mail =
                new MailEntity(userId, "标题" + id, "简介", "正文", "系统", "[]", SEND_AT, null);
        ReflectionTestUtils.setField(mail, "id", id);
        if (read) {
            mail.markRead(SEND_AT.plusSeconds(60));
        }
        return mail;
    }
}
