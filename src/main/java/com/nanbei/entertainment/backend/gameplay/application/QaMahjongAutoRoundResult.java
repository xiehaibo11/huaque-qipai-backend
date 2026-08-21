package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

record QaMahjongAutoRoundResult(
        GamePhase phase,
        int roundNumber,
        long revision,
        JsonNode state,
        List<GameEvent> events,
        Map<Integer, Long> scoreDeltasBySeat) {
    QaMahjongAutoRoundResult {
        events = List.copyOf(events);
        scoreDeltasBySeat = Map.copyOf(scoreDeltasBySeat);
    }
}
