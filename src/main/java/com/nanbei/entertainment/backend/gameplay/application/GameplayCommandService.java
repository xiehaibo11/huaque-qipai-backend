package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.AutoReadyCommand;
import com.nanbei.entertainment.backend.gameplay.domain.CommandResult;
import com.nanbei.entertainment.backend.gameplay.domain.GameActionNotAllowedException;
import com.nanbei.entertainment.backend.gameplay.domain.GameCommandEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.domain.ReadyCommand;
import com.nanbei.entertainment.backend.gameplay.domain.StaleGameCommandException;
import com.nanbei.entertainment.backend.gameplay.domain.WaitingRoomEngine;
import com.nanbei.entertainment.backend.gameplay.domain.WaitingRoomState;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameCommandRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.TaizhouMahjongRuleDisplay;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class GameplayCommandService {
    /** 8 个回合命令：仅已进入 QA 或 SERVER_AUTHORITY 回合的会话受理。 */
    private static final Set<GameplayCommandType> ROUND_COMMANDS =
            Set.of(
                    GameplayCommandType.DISCARD,
                    GameplayCommandType.CHOW,
                    GameplayCommandType.PUNG,
                    GameplayCommandType.KONG,
                    GameplayCommandType.HU,
                    GameplayCommandType.PASS,
                    GameplayCommandType.MULTIPLE_CHOICE,
                    GameplayCommandType.NEXT_ROUND);

    private final GameRoomRepository roomRepository;
    private final GameSessionRepository sessionRepository;
    private final GameSessionSeatRepository seatRepository;
    private final GameCommandRepository commandRepository;
    private final GameEventRepository eventRepository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final QaGameplayBotService qaBotService;
    private final QaRoundCoordinator qaRoundCoordinator;
    private final WaitingRoomEngine waitingRoomEngine = new WaitingRoomEngine();

    @Autowired
    public GameplayCommandService(
            GameRoomRepository roomRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            GameCommandRepository commandRepository,
            GameEventRepository eventRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            QaGameplayBotService qaBotService) {
        this(
                roomRepository,
                sessionRepository,
                seatRepository,
                commandRepository,
                eventRepository,
                cryptoService,
                objectMapper,
                Clock.systemUTC(),
                qaBotService);
    }

    GameplayCommandService(
            GameRoomRepository roomRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            GameCommandRepository commandRepository,
            GameEventRepository eventRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            Clock clock) {
        this(
                roomRepository,
                sessionRepository,
                seatRepository,
                commandRepository,
                eventRepository,
                cryptoService,
                objectMapper,
                clock,
                null);
    }

    GameplayCommandService(
            GameRoomRepository roomRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            GameCommandRepository commandRepository,
            GameEventRepository eventRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            Clock clock,
            QaGameplayBotService qaBotService) {
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.seatRepository = seatRepository;
        this.commandRepository = commandRepository;
        this.eventRepository = eventRepository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.qaBotService = qaBotService;
        this.qaRoundCoordinator = new QaRoundCoordinator(qaBotService, objectMapper);
    }

    @Transactional(noRollbackFor = GameplaySessionCompletedException.class)
    public GameplayCommandResponse submit(
            UUID userId,
            String roomNumber,
            String idempotencyKey,
            GameplayCommandRequest request) {
        String safeKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = cryptoService.sha256(request.canonicalValue(roomNumber));
        commandRepository.acquireCommandLock("game-command:" + userId + ":" + safeKey);
        GameCommandEntity existing =
                commandRepository
                        .findByUserIdAndIdempotencyKey(userId, safeKey)
                        .orElse(null);
        if (existing != null) {
            return replay(existing, requestHash);
        }

        GameRoomEntity room =
                roomRepository
                        .findByRoomNumber(roomNumber)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在"));
        GameSessionEntity session =
                sessionRepository
                        .findLockedByRoomId(room.getId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GAMEPLAY_SESSION_NOT_FOUND,
                                                "牌局尚未创建"));
        GameSessionSeatEntity actorSeat =
                seatRepository
                        .findByIdSessionIdAndUserId(session.getId(), userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GAMEPLAY_FORBIDDEN,
                                                "无权操作该牌局"));
        List<GameSessionSeatEntity> seats =
                seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId());
        Instant now = clock.instant();
        if (request.type() == GameplayCommandType.QA_AUTO_ROUND) {
            return submitQaAutoRound(
                    userId, safeKey, requestHash, request, room, session, actorSeat, seats, now);
        }
        if (request.type() == GameplayCommandType.START_ROUND) {
            return submitStartRound(
                    userId, safeKey, requestHash, request, room, session, actorSeat, seats, now);
        }
        if (request.type() == GameplayCommandType.EARLY_START) {
            return submitEarlyStart(
                    userId, safeKey, requestHash, request, room, session, actorSeat, seats, now);
        }
        if (ROUND_COMMANDS.contains(request.type())) {
            return submitRoundCommand(
                    userId, safeKey, requestHash, request, room, session, actorSeat, seats, now);
        }
        WaitingRoomState state = waitingState(session, seats);
        CommandResult<WaitingRoomState> result = handle(state, room, userId, request);
        WaitingRoomState next = result.state();
        GameEvent event = result.events().getFirst();

        for (GameSessionSeatEntity seat : seats) {
            boolean nextReady = next.isReady(seat.getId().getSeatNumber());
            if (seat.isReady() != nextReady) {
                seat.setReady(nextReady, now);
            }
        }
        session.advance(
                next.phase(),
                session.getRoundNumber(),
                next.revision(),
                json(next),
                now);
        for (int index = 0; index < result.events().size(); index++) {
            GameEvent orderedEvent = result.events().get(index);
            eventRepository.save(
                    eventEntity(session.getId(), orderedEvent, index + 1, now));
        }

        GameplayCommandResponse response =
                new GameplayCommandResponse(
                        next.revision(),
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
        command.accept(next.revision(), json(response));
        commandRepository.save(command);
        return response;
    }


    private GameplayCommandResponse submitQaAutoRound(
            UUID userId,
            String safeKey,
            String requestHash,
            GameplayCommandRequest request,
            GameRoomEntity room,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            List<GameSessionSeatEntity> currentSeats,
            Instant now) {
        if (request.expectedRevision() != session.getRevision()) {
            throw new ApiException(
                    ErrorCode.GAME_COMMAND_STALE,
                    "牌局状态已更新，当前修订号为 " + session.getRevision());
        }
        if (session.getPhase() != com.nanbei.entertainment.backend.gameplay.domain.GamePhase.WAITING) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "QA 自动牌局只能从等待阶段开始");
        }
        QaRoundCoordinator.QaRoundCommandOutcome outcome =
                qaRoundCoordinator.startAutoRound(
                        room, session, currentSeats, request.expectedRevision(), now);
        return persistRoundOutcome(userId, safeKey, requestHash, request, session, actorSeat, outcome, now);
    }

    private GameplayCommandResponse submitStartRound(
            UUID userId,
            String safeKey,
            String requestHash,
            GameplayCommandRequest request,
            GameRoomEntity room,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            List<GameSessionSeatEntity> currentSeats,
            Instant now) {
        QaRoundCoordinator.QaRoundCommandOutcome outcome =
                qaRoundCoordinator.startServerAuthoritativeRound(
                        room, session, userId, currentSeats, request.expectedRevision(), now);
        return persistRoundOutcome(userId, safeKey, requestHash, request, session, actorSeat, outcome, now);
    }

    /**
     * EARLY_START（南北自建 QA 简化，对齐 msgAdvanceStart 语义但不含同意/拒绝交互）：
     * 等待态房主立即用 QA 假人补齐空位并启动完整轮转，与 QA_AUTO_ROUND 同路径；
     * 非房主与非 QA 配置一律拒绝，QA 假人视为同意。
     */
    private GameplayCommandResponse submitEarlyStart(
            UUID userId,
            String safeKey,
            String requestHash,
            GameplayCommandRequest request,
            GameRoomEntity room,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            List<GameSessionSeatEntity> currentSeats,
            Instant now) {
        if (request.expectedRevision() != session.getRevision()) {
            throw new ApiException(
                    ErrorCode.GAME_COMMAND_STALE,
                    "牌局状态已更新，当前修订号为 " + session.getRevision());
        }
        if (session.getPhase() != com.nanbei.entertainment.backend.gameplay.domain.GamePhase.WAITING) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "提前开局只能在等待阶段发起");
        }
        if (!room.getOwnerUserId().equals(userId)) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "只有房主可以提前开局");
        }
        if (!qaRoundCoordinator.enabled()) {
            throw new ApiException(
                    ErrorCode.GAME_ACTION_NOT_ALLOWED,
                    "提前开局只允许在测试/调试配置中启用；正式开局请使用 START_ROUND");
        }
        QaRoundCoordinator.QaRoundCommandOutcome outcome =
                qaRoundCoordinator.startAutoRound(
                        room, session, currentSeats, request.expectedRevision(), now);
        return persistRoundOutcome(userId, safeKey, requestHash, request, session, actorSeat, outcome, now);
    }

    private GameplayCommandResponse submitRoundCommand(
            UUID userId,
            String safeKey,
            String requestHash,
            GameplayCommandRequest request,
            GameRoomEntity room,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            List<GameSessionSeatEntity> seats,
            Instant now) {
        if (request.expectedRevision() != session.getRevision()) {
            throw new ApiException(
                    ErrorCode.GAME_COMMAND_STALE,
                    "牌局状态已更新，当前修订号为 " + session.getRevision());
        }
        QaRoundCoordinator.QaRoundCommandOutcome outcome =
                qaRoundCoordinator.applyCommand(
                        room, session, actorSeat, seats, request.type(), request.payload(), now);
        return persistRoundOutcome(userId, safeKey, requestHash, request, session, actorSeat, outcome, now);
    }

    private GameplayCommandResponse persistRoundOutcome(
            UUID userId,
            String safeKey,
            String requestHash,
            GameplayCommandRequest request,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            QaRoundCoordinator.QaRoundCommandOutcome outcome,
            Instant now) {
        for (GameSessionSeatEntity seat : outcome.seats()) {
            Long delta = outcome.scoreDeltasBySeat().get(seat.getId().getSeatNumber());
            if (delta != null && delta != 0L) {
                seat.applyScoreDelta(delta, now);
            }
        }
        session.advance(
                outcome.phase(),
                outcome.roundNumber(),
                outcome.revision(),
                json(outcome.state()),
                now);
        for (int index = 0; index < outcome.events().size(); index++) {
            GameEvent orderedEvent = outcome.events().get(index);
            eventRepository.save(
                    eventEntity(session.getId(), orderedEvent, index + 1, now));
        }
        GameplayCommandResponse response =
                new GameplayCommandResponse(
                        outcome.revision(),
                        outcome.events().getFirst().type(),
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
        command.accept(outcome.revision(), json(response));
        commandRepository.save(command);
        return response;
    }

    private CommandResult<WaitingRoomState> handle(
            WaitingRoomState state,
            GameRoomEntity room,
            UUID userId,
            GameplayCommandRequest request) {
        if (request.type() != GameplayCommandType.READY
                && request.type() != GameplayCommandType.UNREADY) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "等待阶段不支持该操作");
        }
        try {
            if (request.type() == GameplayCommandType.READY
                    && TaizhouMahjongRuleDisplay.isAutoReady(room.getGameRule())) {
                return waitingRoomEngine.handle(
                        state, new AutoReadyCommand(userId, request.expectedRevision()));
            }
            return waitingRoomEngine.handle(
                    state,
                    new ReadyCommand(
                            userId,
                            request.expectedRevision(),
                            request.type() == GameplayCommandType.READY));
        } catch (StaleGameCommandException exception) {
            throw new ApiException(
                    ErrorCode.GAME_COMMAND_STALE,
                    "牌局状态已更新，当前修订号为 " + exception.currentRevision());
        } catch (GameActionNotAllowedException exception) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, exception.getMessage());
        }
    }

    private GameplayCommandResponse replay(GameCommandEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(
                    ErrorCode.GAME_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key 已用于不同的牌局命令");
        }
        if (existing.getAcceptedRevision() == null) {
            throw new ApiException(
                    ErrorCode.GAME_IDEMPOTENCY_CONFLICT,
                    "牌局命令仍在处理中");
        }
        try {
            return objectMapper
                    .readValue(existing.getResult(), GameplayCommandResponse.class)
                    .asReplay();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read gameplay command result", exception);
        }
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

    private static WaitingRoomState waitingState(
            GameSessionEntity session, List<GameSessionSeatEntity> seats) {
        Map<Integer, UUID> occupants =
                seats.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        seat -> seat.getId().getSeatNumber(),
                                        GameSessionSeatEntity::getUserId));
        Set<Integer> readySeats =
                seats.stream()
                        .filter(GameSessionSeatEntity::isReady)
                        .map(seat -> seat.getId().getSeatNumber())
                        .collect(Collectors.toUnmodifiableSet());
        return new WaitingRoomState(
                session.getGameId(),
                session.getPhase(),
                session.getRevision(),
                occupants,
                readySeats);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize gameplay state", exception);
        }
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不能为空");
        }
        String safeKey = idempotencyKey.trim();
        if (safeKey.length() > 128) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 过长");
        }
        return safeKey;
    }
}
