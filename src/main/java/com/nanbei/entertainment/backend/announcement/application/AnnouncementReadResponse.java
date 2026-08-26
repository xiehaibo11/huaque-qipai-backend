package com.nanbei.entertainment.backend.announcement.application;

import java.time.Instant;

public record AnnouncementReadResponse(
        long announcementId, long version, boolean read, Instant readAt) {}
