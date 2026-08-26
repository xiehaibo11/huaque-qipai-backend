package com.nanbei.entertainment.backend.user.domain;

import com.nanbei.entertainment.backend.common.profile.ProfileSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class UserEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_name_source", nullable = false, length = 20)
    private ProfileSource displayNameSource;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserEntity() {}

    private UserEntity(UUID id, String displayName) {
        this.id = id;
        this.status = UserStatus.ACTIVE;
        this.displayName = displayName;
        this.displayNameSource = ProfileSource.SYSTEM;
    }

    public static UserEntity create(String displayName) {
        return new UserEntity(UUID.randomUUID(), displayName);
    }

    public static UserEntity create(String displayName, ProfileSource source) {
        UserEntity user = create(displayName);
        user.displayNameSource = source;
        return user;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void rename(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.displayName = displayName;
    }

    public void renameFromWechat(String displayName) {
        if (displayNameSource == ProfileSource.USER) {
            return;
        }
        rename(displayName);
        displayNameSource = ProfileSource.WECHAT;
    }

    public void clearWechatDisplayName() {
        if (displayNameSource == ProfileSource.WECHAT) {
            displayName = "微信用户";
            displayNameSource = ProfileSource.SYSTEM;
        }
    }

    public ProfileSource getDisplayNameSource() {
        return displayNameSource;
    }

    public long getAuthVersion() {
        return authVersion;
    }

    public void invalidateSessions() {
        authVersion++;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void deactivate() {
        status = UserStatus.DISABLED;
    }
}
