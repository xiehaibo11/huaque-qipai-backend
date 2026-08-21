package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** QA 完整轮转启动结果：首个真人动作点（或局终）之前的事件链与快照状态。 */
record QaTaizhouRoundResult(
        GamePhase phase,
        int roundNumber,
        long revision,
        JsonNode state,
        List<GameEvent> events,
        Map<Integer, Long> scoreDeltasBySeat,
        QaRoundTable table) {
    QaTaizhouRoundResult {
        events = List.copyOf(events);
        scoreDeltasBySeat = Map.copyOf(scoreDeltasBySeat);
    }
}

/** 单条真人命令产生的事件与结果；roundFinished 时 scoreDeltasBySeat 才有值。NEXT_ROUND 会换上新牌桌。 */
record QaRoundStep(
        List<GameEvent> events,
        Map<Integer, Long> scoreDeltasBySeat,
        boolean roundFinished,
        QaRoundTable table) {
    QaRoundStep {
        events = List.copyOf(events);
        scoreDeltasBySeat = Map.copyOf(scoreDeltasBySeat);
    }
}
