package com.nanbei.entertainment.backend.announcement.application;

import java.util.List;

public record AnnouncementListResponse(long lobbyId, List<AnnouncementSummary> announcements) {
    public AnnouncementListResponse {
        announcements = List.copyOf(announcements);
    }
}
