package com.nanbei.entertainment.backend.mail.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.mail.domain.MailAttachment;
import com.nanbei.entertainment.backend.mail.domain.MailEntity;
import com.nanbei.entertainment.backend.mail.domain.MailRewardType;
import com.nanbei.entertainment.backend.mail.infrastructure.MailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Internal delivery boundary used by trusted game, activity and compensation services. */
@Service
public class MailDeliveryService {
    private static final int MAX_ATTACHMENTS = 10;
    private final MailRepository repository;
    private final ObjectMapper objectMapper;

    public MailDeliveryService(MailRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MailEntity deliver(MailDeliveryCommand command) {
        validate(command);
        String sourceType = command.sourceType().trim();
        String sourceId = command.sourceId().trim();
        return repository
                .findByUserIdAndSourceTypeAndSourceId(
                        command.userId(), sourceType, sourceId)
                .orElseGet(() -> repository.save(toEntity(command, sourceType, sourceId)));
    }

    private MailEntity toEntity(
            MailDeliveryCommand command, String sourceType, String sourceId) {
        try {
            return new MailEntity(
                    command.userId(),
                    sourceType,
                    sourceId,
                    command.title().trim(),
                    nullable(command.intro()),
                    nullable(command.content()),
                    nullable(command.sender()),
                    objectMapper.writeValueAsString(command.attachments()),
                    command.sendAt(),
                    command.expireAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize mail attachments", exception);
        }
    }

    private static void validate(MailDeliveryCommand command) {
        if (command == null
                || command.userId() == null
                || blank(command.sourceType(), 80)
                || blank(command.sourceId(), 160)
                || blank(command.title(), 200)
                || command.sendAt() == null
                || command.attachments().size() > MAX_ATTACHMENTS
                || (command.expireAt() != null
                        && !command.expireAt().isAfter(command.sendAt()))) {
            invalid();
        }
        for (MailAttachment attachment : command.attachments()) {
            if (attachment == null || attachment.amount() <= 0) invalid();
            try {
                MailRewardType.valueOf(attachment.rewardType());
            } catch (IllegalArgumentException | NullPointerException exception) {
                invalid();
            }
        }
    }

    private static boolean blank(String value, int maxLength) {
        return value == null || value.trim().isEmpty() || value.trim().length() > maxLength;
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private static void invalid() {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "邮件投递参数不正确");
    }
}
