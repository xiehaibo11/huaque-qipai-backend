package com.nanbei.entertainment.backend.personalcenter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_privacy_settings")
public class PrivacySettingsEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "allow_friend_requests", nullable = false)
    private boolean allowFriendRequests;

    @Column(name = "show_game_record", nullable = false)
    private boolean showGameRecord;

    @Column(name = "show_online_status", nullable = false)
    private boolean showOnlineStatus;

    @Column(name = "chat_notifications", nullable = false)
    private boolean chatNotifications;

    @Column(name = "personalized_recommendations", nullable = false)
    private boolean personalizedRecommendations;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PrivacySettingsEntity() {}

    public PrivacySettingsEntity(UUID userId) {
        this.userId = userId;
        allowFriendRequests = true;
        showGameRecord = true;
        showOnlineStatus = true;
        chatNotifications = true;
        personalizedRecommendations = false;
    }

    public void update(
            boolean allowFriendRequests,
            boolean showGameRecord,
            boolean showOnlineStatus,
            boolean chatNotifications,
            boolean personalizedRecommendations) {
        this.allowFriendRequests = allowFriendRequests;
        this.showGameRecord = showGameRecord;
        this.showOnlineStatus = showOnlineStatus;
        this.chatNotifications = chatNotifications;
        this.personalizedRecommendations =
                personalizedRecommendations;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isAllowFriendRequests() {
        return allowFriendRequests;
    }

    public boolean isShowGameRecord() {
        return showGameRecord;
    }

    public boolean isShowOnlineStatus() {
        return showOnlineStatus;
    }

    public boolean isChatNotifications() {
        return chatNotifications;
    }

    public boolean isPersonalizedRecommendations() {
        return personalizedRecommendations;
    }
}
