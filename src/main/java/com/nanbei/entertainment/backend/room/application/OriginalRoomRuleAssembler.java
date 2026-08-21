package com.nanbei.entertainment.backend.room.application;

import java.util.Map;

final class OriginalRoomRuleAssembler {
    private static final int BOX_ROOM_MODE = 10;
    private static final String[] ORIGINAL_CONDITION_ORDER = {
        "ConditionRoomType", "IsJuMa", "roomtype"
    };

    private OriginalRoomRuleAssembler() {}

    static String assemble(long gameId, RoomRuleSelection selection) {
        return assemble(gameId, selection.playerCount(), selection.roomConditions());
    }

    static String assemble(long gameId, int playerCount, Map<String, String> conditions) {
        StringBuilder roomRule =
                new StringBuilder("roomrule={GamePlayerCount=\"")
                        .append(playerCount)
                        .append("\",group=\"")
                        .append(gameId)
                        .append("\",cancreate=\"1\",roommode=\"")
                        .append(BOX_ROOM_MODE)
                        .append("\"");
        for (String key : ORIGINAL_CONDITION_ORDER) {
            String value = conditions == null ? null : conditions.get(key);
            if (value != null && !value.isBlank()) {
                roomRule.append(",").append(key).append("=\"").append(value).append("\"");
            }
        }
        return roomRule.append("}").toString();
    }

}
