package com.nanbei.entertainment.backend.matcharena.application;

import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaCostType;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaLevel;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMemberRole;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMode;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaStatus;
import java.time.Instant;

public record MatchArenaResponse(
        String id,
        String arenaNumber,
        long lobbyId,
        String areaName,
        String remark,
        MatchArenaLevel level,
        MatchArenaMode mode,
        MatchArenaCostType costType,
        int originalPayType,
        MatchArenaMemberRole role,
        long ownerPublicPlayerId,
        String ownerNickname,
        String ownerAvatarKey,
        long roomCards,
        long dailyRoomCardLimit,
        boolean visibleToStrangers,
        boolean autoTransferEnabled,
        long autoTransferThreshold,
        long autoTransferAmount,
        Long lowCardReminderThreshold,
        MatchArenaStatus status,
        int memberCount,
        int onlineCount,
        Instant createdAt,
        long version,
        boolean duplicate) {}
