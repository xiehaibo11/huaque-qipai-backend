package com.nanbei.entertainment.backend.room.application;

import java.util.Map;

public record RoomRuleSelection(
        String gameRule,
        String gameRuleDisplay,
        int playerCount,
        int playCount,
        RoomPayType payType,
        int roomFeeCenti,
        int roomMode,
        Map<String, String> roomConditions) {
    public RoomRuleSelection {
        roomConditions = Map.copyOf(roomConditions);
    }
}
