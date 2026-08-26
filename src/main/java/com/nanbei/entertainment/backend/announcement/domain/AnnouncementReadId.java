package com.nanbei.entertainment.backend.announcement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AnnouncementReadId implements Serializable {
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    protected AnnouncementReadId() {}

    public AnnouncementReadId(UUID userId, Long announcementId) {
        this.userId = userId;
        this.announcementId = announcementId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getAnnouncementId() {
        return announcementId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnnouncementReadId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(announcementId, that.announcementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, announcementId);
    }
}
