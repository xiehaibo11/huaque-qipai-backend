package com.nanbei.entertainment.backend.mail.application;

import java.time.Instant;

public record MailListItem(
        long mailId,
        String title,
        String intro,
        String sender,
        boolean hasAttachment,
        boolean read,
        boolean claimed,
        Instant sendTime,
        Instant expireTime) {}
