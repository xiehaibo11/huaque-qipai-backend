package com.nanbei.entertainment.backend.announcement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcement_reads")
public class AnnouncementReadEntity {
    @EmbeddedId
    private AnnouncementReadId id;

    @Column(name = "announcement_version", nullable = false)
    private long announcementVersion;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected AnnouncementReadEntity() {}

    public AnnouncementReadEntity(
            UUID userId, Long announcementId, long announcementVersion, Instant readAt) {
        this.id = new AnnouncementReadId(userId, announcementId);
        this.announcementVersion = announcementVersion;
        this.readAt = readAt;
    }

    public long getAnnouncementId() {
        return id.getAnnouncementId();
    }

    public long getAnnouncementVersion() {
        return announcementVersion;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void mark(long version, Instant now) {
        announcementVersion = version;
        readAt = now;
    }
}
