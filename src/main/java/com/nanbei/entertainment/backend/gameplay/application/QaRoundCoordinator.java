package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.room.application.TaizhouMahjongRuleDisplay;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 台州麻将完整轮转的会话级协调：QA 假人链路、生产开局门禁、引擎调用与状态读写。
 * QA 会话继续要求 qaDisclosure 与 QA 配置；SERVER_AUTHORITY 会话由 START_ROUND 创建，
 * 后续牌局命令不依赖 QA 开关。
 */
final class QaRoundCoordinator {
    private final QaGameplayBotService qaBotService;
    private final ObjectMapper objectMapper;

    QaRoundCoordinator(QaGameplayBotService qaBotService, ObjectMapper objectMapper) {
        this.qaBotService = qaBotService;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    boolean enabled() {
        return qaBotService != null && qaBotService.enabled();
    }

    /** QA_AUTO_ROUND：补满假人后启动完整轮转，停在首个真人动作点或局终。 */
    QaRoundCommandOutcome startAutoRound(
            GameRoomEntity room,
            GameSessionEntity session,
            List<GameSessionSeatEntity> currentSeats,
            long expectedRevision,
            Instant now) {
        if (!enabled()) {
            throw new ApiException(
                    ErrorCode.GAME_ACTION_NOT_ALLOWED, "QA 自动牌局只允许在测试/调试配置中启用");
        }
        List<GameSessionSeatEntity> seats =
                qaBotService.ensureTenBotsAndFillSeats(room, session, currentSeats, now);
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(objectMapper);
        QaTaizhouRoundResult result =
                engine.start(
                        new QaTaizhouRoundEngine.Request(
                                session.getGameId(),
                                room.getRoomNumber(),
                                room.getPlayerCount(),
                                room.getPlayCount(),
                                gameRuleDisplay(room),
                                expectedRevision,
                                session.getRoundNumber(),
                                qaBotService.seatInputs(room, seats),
                                now));
        return new QaRoundCommandOutcome(
                result.phase(),
                result.roundNumber(),
                result.revision(),
                result.state(),
                result.events(),
                result.scoreDeltasBySeat(),
                seats);
    }

    /** START_ROUND：满员、全员准备、无测试假人的正式服务端权威开局。 */
    QaRoundCommandOutcome startServerAuthoritativeRound(
            GameRoomEntity room,
            GameSessionEntity session,
            UUID actorUserId,
            List<GameSessionSeatEntity> seats,
            long expectedRevision,
            Instant now) {
        if (expectedRevision != session.getRevision()) {
            throw new ApiException(
                    ErrorCode.GAME_COMMAND_STALE,
                    "牌局状态已更新，当前修订号为 " + session.getRevision());
        }
        if (session.getPhase() != GamePhase.WAITING) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "当前阶段不能开始首局");
        }
        if (!room.getOwnerUserId().equals(actorUserId)) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "只有房主可以开始首局");
        }
        if (seats.size() != room.getPlayerCount()) {
            throw new ApiException(ErrorCode.ROOM_NOT_FULL, "房间未满员，不能开始首局");
        }
        if (seats.stream().anyMatch(seat -> !seat.isReady())) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "还有玩家未准备，不能开始首局");
        }
        List<QaMahjongAutoRoundEngine.SeatInput> seatInputs = seatInputs(room, seats);
        if (seatInputs.stream().anyMatch(QaMahjongAutoRoundEngine.SeatInput::qaBot)) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "生产首局不能包含测试假人");
        }
        QaTaizhouRoundEngine engine =
                new QaTaizhouRoundEngine(objectMapper, TaizhouRoundMode.SERVER_AUTHORITY);
        QaTaizhouRoundResult result =
                engine.start(
                        new QaTaizhouRoundEngine.Request(
                                session.getGameId(),
                                room.getRoomNumber(),
                                room.getPlayerCount(),
                                room.getPlayCount(),
                                gameRuleDisplay(room),
                                expectedRevision,
                                session.getRoundNumber(),
                                seatInputs,
                                now,
                                room.getRoomMode() == 50));
        return new QaRoundCommandOutcome(
                result.phase(),
                result.roundNumber(),
                result.revision(),
                result.state(),
                result.events(),
                result.scoreDeltasBySeat(),
                seats);
    }

    /** 8 个牌局命令：QA 会话要求 QA 开关，生产 SERVER_AUTHORITY 会话直接受理。 */
    QaRoundCommandOutcome applyCommand(
            GameRoomEntity room,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            List<GameSessionSeatEntity> seats,
            GameplayCommandType type,
            JsonNode payload,
            Instant now) {
        JsonNode state = readState(session);
        TaizhouRoundMode mode = TaizhouRoundMode.fromSessionState(state);
        if (mode == null || state.path("qaRound").isMissingNode()) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "牌局尚未开始");
        }
        if (mode.qaMode() && !enabled()) {
            throw new ApiException(
                    ErrorCode.GAME_ACTION_NOT_ALLOWED, "QA 自动牌局只允许在测试/调试配置中启用");
        }
        if (type == GameplayCommandType.NEXT_ROUND) {
            guardNextRound(room, session, now);
            if (mode.qaMode() && isQaGoldRoom(room)) {
                seats = qaBotService.replaceIneligibleGoldBots(room, session, seats, now);
            }
        }
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(objectMapper, mode);
        QaRoundTable table = engine.readTable(state);
        QaRoundContext context =
                new QaRoundContext(
                        room.getRoomNumber(),
                        gameRuleDisplay(room),
                        seatInputs(room, seats),
                        now,
                        room.getRoomMode() == 50);
        QaRoundStep step =
                engine.apply(
                        table,
                        context,
                        actorSeat.getId().getSeatNumber(),
                        type,
                        payload,
                        session.getRevision() + 1L);
        JsonNode nextState = engine.sessionState(step.table(), context);
        return new QaRoundCommandOutcome(
                QaTaizhouRoundEngine.phaseOf(step.table()),
                step.table().roundNumber,
                session.getRevision() + 1L,
                nextState,
                step.events(),
                step.scoreDeltasBySeat(),
                seats);
    }

    /**
     * NEXT_ROUND 门禁（南北自建多局流转）：仅 ROUND_RESULT 且局数未尽时放行；
     * 局数用尽时把会话置 COMPLETED 并拒绝（大结算暂未实现，标注自建）。
     */
    private static void guardNextRound(GameRoomEntity room, GameSessionEntity session, Instant now) {
        if (session.getPhase() != GamePhase.ROUND_RESULT) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "当前阶段不能开启下一局");
        }
        if (session.getRoundNumber() >= room.getPlayCount()) {
            session.complete(now);
            throw new GameplaySessionCompletedException("已达最大局数，牌局完结（大结算暂未实现）");
        }
    }

    private List<QaMahjongAutoRoundEngine.SeatInput> seatInputs(
            GameRoomEntity room, List<GameSessionSeatEntity> seats) {
        if (qaBotService == null) {
            throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "牌局玩家资料不完整");
        }
        return qaBotService.seatInputs(room, seats);
    }

    private static boolean isQaGoldRoom(GameRoomEntity room) {
        return room.getGameRule() != null && room.getGameRule().contains("QaGoldMatch='1'");
    }

    private JsonNode readState(GameSessionEntity session) {
        try {
            JsonNode state = objectMapper.readTree(session.getState());
            return state == null || state.isNull() ? objectMapper.createObjectNode() : state;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read gameplay session state", exception);
        }
    }

    private static String gameRuleDisplay(GameRoomEntity room) {
        if (!room.getGameRuleDisplay().isBlank()) {
            return room.getGameRuleDisplay();
        }
        return TaizhouMahjongRuleDisplay.render(
                room.getGameRule(), room.getPlayerCount(), room.getPlayCount(), room.getPayType());
    }

    record QaRoundCommandOutcome(
            GamePhase phase,
            int roundNumber,
            long revision,
            JsonNode state,
            List<GameEvent> events,
            Map<Integer, Long> scoreDeltasBySeat,
            List<GameSessionSeatEntity> seats) {
        QaRoundCommandOutcome {
            events = List.copyOf(events);
            scoreDeltasBySeat = Map.copyOf(scoreDeltasBySeat);
            seats = List.copyOf(seats);
        }
    }
}
