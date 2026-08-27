package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameCommandEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameCommandRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 回合命令结果落库：座位分差、会话推进、事件与幂等命令一并持久化。 */
final class GameplayRoundOutcomePersister {
    private final GameEventRepository eventRepository;
    private final GameCommandRepository commandRepository;
    private final ObjectMapper objectMapper;

    GameplayRoundOutcomePersister(
            GameEventRepository eventRepository,
            GameCommandRepository commandRepository,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.commandRepository = commandRepository;
        this.objectMapper = objectMapper;
    }

    GameplayCommandResponse persist(
            UUID userId,
            String safeKey,
            String requestHash,
            GameplayCommandRequest request,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            QaRoundCoordinator.QaRoundCommandOutcome outcome,
            Instant now) {
        persistCore(session, outcome, now);
        GameplayCommandResponse response =
                new GameplayCommandResponse(
                        outcome.revision(),
                        outcome.events().getFirst().type(),
                        actorSeat.getId().getSeatNumber(),
                        actorSeat.isReady(),
                        false, GameplayEventView.visibleTo(
                                objectMapper, session.getId(), actorSeat.getId().getSeatNumber(), outcome.events()));
        GameCommandEntity command =
                new GameCommandEntity(
                        session.getId(),
                        userId,
                        safeKey,
                        requestHash,
                        request.type().name(),
                        request.expectedRevision(),
                        now);
        command.accept(outcome.revision(), json(response));
        commandRepository.save(command);
        return response;
    }

    /**
     * 服务端自驱推进（超时托管代打）落库：不写幂等命令行，其余与 {@link #persist} 一致。
     */
    void persistServerAdvance(
            GameSessionEntity session,
            QaRoundCoordinator.QaRoundCommandOutcome outcome,
            Instant now) {
        persistCore(session, outcome, now);
    }

    private void persistCore(GameSessionEntity session, QaRoundCoordinator.QaRoundCommandOutcome outcome, Instant now) {
        for (GameSessionSeatEntity seat : outcome.seats()) {
            Long delta = outcome.scoreDeltasBySeat().get(seat.getId().getSeatNumber());
            if (delta != null && delta != 0L) {
                seat.applyScoreDelta(delta, now);
            }
        }
        session.advance(
                persistedPhase(outcome.phase(), outcome.state()),
                outcome.roundNumber(),
                outcome.revision(),
                json(outcome.state()),
                now);
        for (int index = 0; index < outcome.events().size(); index++) {
            GameEvent orderedEvent = outcome.events().get(index);
            eventRepository.save(
                    eventEntity(session.getId(), orderedEvent, index + 1, now));
        }
    }

    static GamePhase persistedPhase(GamePhase phase, JsonNode state) {
        return phase == GamePhase.ROUND_RESULT
                        && state != null && state.hasNonNull("totalResult")
                ? GamePhase.COMPLETED : phase;
    }

    private GameEventEntity eventEntity(
            UUID sessionId, GameEvent event, int eventOrder, Instant occurredAt) {
        if (event.audience() == GameEvent.Audience.SEAT) {
            return GameEventEntity.seatEvent(
                    sessionId,
                    event.revision(),
                    eventOrder,
                    event.type(),
                    event.targetSeat(),
                    json(event.payload()),
                    occurredAt);
        }
        return GameEventEntity.publicEvent(
                sessionId,
                event.revision(),
                eventOrder,
                event.type(),
                json(event.payload()),
                occurredAt);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize gameplay state", exception);
        }
    }
}
