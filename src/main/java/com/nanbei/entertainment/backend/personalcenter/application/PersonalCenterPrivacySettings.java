package com.nanbei.entertainment.backend.personalcenter.application;

public record PersonalCenterPrivacySettings(
        boolean allowFriendRequests,
        boolean showGameRecord,
        boolean showOnlineStatus,
        boolean chatNotifications,
        boolean personalizedRecommendations,
        boolean clipboardAccessEnabled) {
    public static PersonalCenterPrivacySettings defaults() {
        return new PersonalCenterPrivacySettings(
                true, true, true, true, false, true);
    }
}
