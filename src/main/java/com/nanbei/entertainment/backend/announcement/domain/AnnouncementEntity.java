package com.nanbei.entertainment.backend.announcement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lobby_announcements")
public class AnnouncementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 300)
    private String subtitle;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @Column(name = "page_url", length = 2048)
    private String pageUrl;

    @Column(name = "lobby_id")
    private Long lobbyId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false)
    private long version;

    protected AnnouncementEntity() {}

    public AnnouncementEntity(
            String title,
            String subtitle,
            String bodyText,
            String pageUrl,
            Long lobbyId,
            int sortOrder,
            boolean enabled,
            Instant startsAt,
            Instant endsAt,
            long version) {
        this.title = title;
        this.subtitle = subtitle;
        this.bodyText = bodyText;
        this.pageUrl = pageUrl;
        this.lobbyId = lobbyId;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.version = version;
    }

    public boolean isVisibleTo(long selectedLobbyId, Instant now) {
        return enabled
                && (lobbyId == null || lobbyId == selectedLobbyId)
                && (startsAt == null || !startsAt.isAfter(now))
                && (endsAt == null || endsAt.isAfter(now));
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public Long getLobbyId() {
        return lobbyId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public long getVersion() {
        return version;
    }
}
