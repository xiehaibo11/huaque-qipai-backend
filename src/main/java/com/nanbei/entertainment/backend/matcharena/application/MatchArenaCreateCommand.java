package com.nanbei.entertainment.backend.matcharena.application;

import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaCostType;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaLevel;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMode;

public record MatchArenaCreateCommand(
        long lobbyId,
        String remark,
        MatchArenaLevel level,
        MatchArenaMode mode,
        MatchArenaCostType costType,
        long initialRoomCards,
        long dailyRoomCardLimit,
        boolean visibleToStrangers,
        boolean autoTransferEnabled,
        long autoTransferThreshold,
        long autoTransferAmount,
        Long lowCardReminderThreshold) {}
