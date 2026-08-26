package com.nanbei.entertainment.backend.announcement.application;

import java.time.Instant;

public record AnnouncementSummary(
        long announcementId,
        String title,
        String subtitle,
        String bodyText,
        String pageUrl,
        Long lobbyId,
        int sortOrder,
        Instant startsAt,
        Instant endsAt,
        long version,
        boolean read) {}
