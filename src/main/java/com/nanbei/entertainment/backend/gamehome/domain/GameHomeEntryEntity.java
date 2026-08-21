package com.nanbei.entertainment.backend.gamehome.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_home_entries")
public class GameHomeEntryEntity {
    @Id
    @Column(length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "entry_type", nullable = false, length = 32)
    private String entryType;

    @Column(nullable = false, length = 120)
    private String route;

    @Column(name = "icon_key", nullable = false, length = 120)
    private String iconKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "lobby_id")
    private Long lobbyId;

    /**
     * 入口气泡文案，对应原版 {@code hall_tip_type_2} 节点的 {@code data:getBubbleText()}。
     * 为空表示该入口不展示气泡。
     */
    @Column(name = "bubble_text", length = 120)
    private String bubbleText;

    /** 原版 {@code getBubbleType()}：2 循环淡入、3 弹跳、4 按次数播完自删；其他值不展示。 */
    @Column(name = "bubble_type")
    private Integer bubbleType;

    /** 原版 {@code getBubbleInterval()}，两次展示之间的间隔秒数，原版缺省 30。 */
    @Column(name = "bubble_interval_seconds")
    private Integer bubbleIntervalSeconds;

    protected GameHomeEntryEntity() {}

    public GameHomeEntryEntity(
            String code,
            String displayName,
            String entryType,
            String route,
            String iconKey,
            int sortOrder,
            boolean enabled,
            Long lobbyId) {
        this(code, displayName, entryType, route, iconKey, sortOrder, enabled, lobbyId,
                null, null, null);
    }

    public GameHomeEntryEntity(
            String code,
            String displayName,
            String entryType,
            String route,
            String iconKey,
            int sortOrder,
            boolean enabled,
            Long lobbyId,
            String bubbleText,
            Integer bubbleType,
            Integer bubbleIntervalSeconds) {
        this.code = code;
        this.displayName = displayName;
        this.entryType = entryType;
        this.route = route;
        this.iconKey = iconKey;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.lobbyId = lobbyId;
        this.bubbleText = bubbleText;
        this.bubbleType = bubbleType;
        this.bubbleIntervalSeconds = bubbleIntervalSeconds;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEntryType() {
        return entryType;
    }

    public String getRoute() {
        return route;
    }

    public String getIconKey() {
        return iconKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Long getLobbyId() {
        return lobbyId;
    }

    public String getBubbleText() {
        return bubbleText;
    }

    public Integer getBubbleType() {
        return bubbleType;
    }

    public Integer getBubbleIntervalSeconds() {
        return bubbleIntervalSeconds;
    }
}
