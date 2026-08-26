package com.nanbei.entertainment.backend.mail.application;

import com.nanbei.entertainment.backend.mail.domain.MailAttachment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MailDeliveryCommand(
        UUID userId,
        String sourceType,
        String sourceId,
        String title,
        String intro,
        String content,
        String sender,
        List<MailAttachment> attachments,
        Instant sendAt,
        Instant expireAt) {
    public MailDeliveryCommand {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
