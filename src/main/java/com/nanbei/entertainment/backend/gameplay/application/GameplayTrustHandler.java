package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameCommandEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameCommandRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class GameplayTrustHandler {
    private final GameCommandRepository commandRepository;
    private final GameEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    GameplayTrustHandler(
            GameCommandRepository commandRepository,
            GameEventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.commandRepository = commandRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    GameplayCommandResponse submit(
            UUID userId,
            String safeKey,
            String requestHash,
            GameplayCommandRequest request,
            GameRoomEntity room,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            Instant now) {
        JsonNode payload = request.payload();
        if (payload == null || !payload.isObject() || !payload.hasNonNull("trusted")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "托管标志缺失");
        }
        long nextRevision = session.getRevision() + 1;
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("seat", actorSeat.getId().getSeatNumber());
        eventPayload.put("trusted", payload.path("trusted").asBoolean(false));
        eventPayload.put("punishSeconds", trustSeconds(room.getGameRule()));
        GameEvent event = GameEvent.publicEvent(nextRevision, "TRUST", eventPayload);
        session.advance(
                session.getPhase(),
                session.getRoundNumber(),
                nextRevision,
                session.getState(),
                now);
        eventRepository.save(eventEntity(session.getId(), event, now));
        GameplayCommandResponse response =
                new GameplayCommandResponse(
                        nextRevision,
                        event.type(),
                        actorSeat.getId().getSeatNumber(),
                        actorSeat.isReady(),
                        false);
        GameCommandEntity command =
                new GameCommandEntity(
                        session.getId(),
                        userId,
                        safeKey,
                        requestHash,
                        request.type().name(),
                        request.expectedRevision(),
                        now);
        command.accept(nextRevision, json(response));
        commandRepository.save(command);
        return response;
    }

    private GameEventEntity eventEntity(UUID sessionId, GameEvent event, Instant occurredAt) {
        return GameEventEntity.publicEvent(
                sessionId,
                event.revision(),
                1,
                event.type(),
                json(event.payload()),
                occurredAt);
    }

    private static int trustSeconds(String gameRule) {
        if (gameRule == null || gameRule.isBlank()) {
            return 0;
        }
        for (String assignment : gameRule.split(";")) {
            int separator = assignment.indexOf('=');
            if (separator <= 0 || separator == assignment.length() - 1) {
                continue;
            }
            if (!"IsSysTrust".equals(assignment.substring(0, separator).trim())) {
                continue;
            }
            String value = assignment.substring(separator + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("'") && value.endsWith("'"))
                            || (value.startsWith("\"") && value.endsWith("\"")))) {
                value = value.substring(1, value.length() - 1);
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write gameplay trust json", exception);
        }
    }
}
