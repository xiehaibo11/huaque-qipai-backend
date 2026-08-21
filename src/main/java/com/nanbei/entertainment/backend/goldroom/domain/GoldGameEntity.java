package com.nanbei.entertainment.backend.goldroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A gold-room (原版"休闲场") game entry shown on the lobby's main grid.
 *
 * <p>The original splits one mahjong title into a box-room ConfID and a gold ConfID; for 台州麻将
 * those are 30109 and 30400. {@code boxGameId} mirrors the original {@code DefaultBoxGameId}, i.e.
 * which box-room game supplies the shared table and rule base class.
 */
@Entity
@Table(name = "gold_games")
public class GoldGameEntity {
    @EmbeddedId private GoldGameId id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "box_game_id")
    private Long boxGameId;

    @Column(name = "gold_mode", nullable = false)
    private int goldMode;

    @Column(name = "chair_count", nullable = false)
    private int chairCount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    protected GoldGameEntity() {}

    public GoldGameId getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Long getBoxGameId() {
        return boxGameId;
    }

    public int getGoldMode() {
        return goldMode;
    }

    public int getChairCount() {
        return chairCount;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
