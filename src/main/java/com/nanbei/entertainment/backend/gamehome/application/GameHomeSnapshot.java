package com.nanbei.entertainment.backend.gamehome.application;

import java.util.List;
import java.util.UUID;

public record GameHomeSnapshot(
        Player player,
        Wallet wallet,
        Region region,
        List<Entry> entries,
        List<Announcement> announcements) {
    public GameHomeSnapshot {
        entries = List.copyOf(entries);
        announcements = List.copyOf(announcements);
    }

    public GameHomeSnapshot(
            Player player,
            Wallet wallet,
            Region region,
            List<Entry> entries) {
        this(player, wallet, region, entries, List.of());
    }

    public record Player(
            UUID userId,
            long publicPlayerId,
            String displayName,
            String avatarKey,
            int membershipLevel) {}

    public record Wallet(
            long roomCards,
            long boundRoomCards,
            long coins,
            long diamonds) {}

    public record Region(long lobbyId, String areaName) {}

    public record Announcement(String content) {}

    /**
     * 大厅入口。{@code bubbleText}、{@code bubbleType}、{@code bubbleIntervalSeconds}
     * 对应原版游戏卡片 {@code hall_tip_type_2} 节点的服务端气泡配置，可空表示不展示气泡。
     */
    public record Entry(
            String code,
            String displayName,
            String entryType,
            String route,
            String iconKey,
            int sortOrder,
            boolean enabled,
            String bubbleText,
            Integer bubbleType,
            Integer bubbleIntervalSeconds) {}
}
