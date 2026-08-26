package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record GameplayEventView(
        UUID sessionId,
        long revision,
        int eventOrder,
        String type,
        JsonNode payload) {
    static List<GameplayEventView> visibleTo(
            ObjectMapper objectMapper,
            UUID sessionId,
            int seatNumber,
            List<GameEvent> events) {
        List<GameplayEventView> visible = new ArrayList<>();
        for (GameEvent event : events) {
            if (event.audience() == GameEvent.Audience.PUBLIC
                    || Integer.valueOf(seatNumber).equals(event.targetSeat())) {
                visible.add(
                        new GameplayEventView(
                                sessionId,
                                event.revision(),
                                visible.size() + 1,
                                event.type(),
                                objectMapper.valueToTree(event.payload())));
            }
        }
        return List.copyOf(visible);
    }
}
