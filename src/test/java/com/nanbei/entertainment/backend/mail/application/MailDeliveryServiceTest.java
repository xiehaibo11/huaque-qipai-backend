package com.nanbei.entertainment.backend.mail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.mail.domain.MailAttachment;
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
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MailDeliveryServiceTest {
    @Mock MailRepository repository;
    MailDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new MailDeliveryService(repository, new ObjectMapper());
    }

    @Test
    void repeatedBusinessSourceReturnsTheExistingMailWithoutDuplicatingDelivery() {
        MailDeliveryCommand command = command(100);
        MailEntity existing = new MailEntity(
                command.userId(), "已有", "", "", "系统", "[]", command.sendAt(), null);
        when(repository.findByUserIdAndSourceTypeAndSourceId(
                        command.userId(), command.sourceType(), command.sourceId()))
                .thenReturn(Optional.of(existing));

        assertThat(service.deliver(command)).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void persistsAValidatedStronglyTypedRewardMail() {
        MailDeliveryCommand command = command(100);
        when(repository.findByUserIdAndSourceTypeAndSourceId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MailEntity delivered = service.deliver(command);

        assertThat(delivered.getSourceType()).isEqualTo("GAME_COMPENSATION");
        assertThat(delivered.getSourceId()).isEqualTo("round-7");
        assertThat(delivered.getAttachments()).contains("\"rewardType\":\"COIN\"");
    }

    @Test
    void rejectsInvalidRewardBeforeItCanEnterTheEconomicLedger() {
        MailDeliveryCommand command = command(0);

        assertThatThrownBy(() -> service.deliver(command)).isInstanceOf(ApiException.class);
        verify(repository, never()).save(any());
    }

    private static MailDeliveryCommand command(long amount) {
        return new MailDeliveryCommand(
                UUID.randomUUID(),
                "GAME_COMPENSATION",
                "round-7",
                "对局补偿",
                "奖励已发放",
                "详情",
                "系统",
                List.of(new MailAttachment("mail_coin", "COIN", amount, "金币")),
                Instant.parse("2026-08-24T12:00:00Z"),
                Instant.parse("2026-09-24T12:00:00Z"));
    }
}
