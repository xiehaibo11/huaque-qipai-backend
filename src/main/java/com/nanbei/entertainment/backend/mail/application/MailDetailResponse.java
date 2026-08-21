package com.nanbei.entertainment.backend.mail.application;

import java.time.Instant;
import java.util.List;

public record MailDetailResponse(
        long mailId,
        String sender,
        String title,
        String content,
        Instant sendTime,
        Instant expireTime,
        boolean read,
        boolean claimed,
        List<MailAttachmentItem> attachments) {}
