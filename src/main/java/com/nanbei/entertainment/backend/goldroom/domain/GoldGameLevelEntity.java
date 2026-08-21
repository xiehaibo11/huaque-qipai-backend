package com.nanbei.entertainment.backend.goldroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One selectable level (档位) of a gold-room game, e.g. 新手场 / 进阶场 / 高级场.
 *
 * <p>Field names follow the original client's consumption points: {@code roomNameFlag} is the
 * original {@code roomnameflag} and also the {@code Level} that drives the card palette
 * ({@code UIType = Level % 10}); {@code minRich}/{@code maxRich} map to the original
 * {@code big_min_score}/{@code big_max_score}, where {@code maxRich == -1} renders as "以上".
 */
@Entity
@Table(name = "gold_game_levels")
public class GoldGameLevelEntity {
    /** Sentinel for "no upper bound", rendered by the client as a trailing 以上. */
    public static final long UNBOUNDED_MAX_RICH = -1L;

    @EmbeddedId private GoldGameLevelId id;

    @Column(name = "chair_count", nullable = false)
    private int chairCount;

    @Column(name = "base_score", nullable = false)
    private long baseScore;

    @Column(name = "dynamic_cost", nullable = false)
    private boolean dynamicCost;

    @Column(name = "min_rich", nullable = false)
    private long minRich;

    @Column(name = "max_rich", nullable = false)
    private long maxRich;

    @Column(name = "tag_lt")
    private String tagLeftTop;

    @Column(name = "tag_rt")
    private String tagRightTop;

    @Column(name = "tag_cr_1")
    private String tagRibbon1;

    @Column(name = "tag_cr_2")
    private String tagRibbon2;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    protected GoldGameLevelEntity() {}

    public GoldGameLevelId getId() {
        return id;
    }

    public int getChairCount() {
        return chairCount;
    }

    public long getBaseScore() {
        return baseScore;
    }

    public boolean isDynamicCost() {
        return dynamicCost;
    }

    public long getMinRich() {
        return minRich;
    }

    public long getMaxRich() {
        return maxRich;
    }

    public String getTagLeftTop() {
        return tagLeftTop;
    }

    public String getTagRightTop() {
        return tagRightTop;
    }

    public String getTagRibbon1() {
        return tagRibbon1;
    }

    public String getTagRibbon2() {
        return tagRibbon2;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
