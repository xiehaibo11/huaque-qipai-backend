package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 南北自建台州完整轮转引擎，非原版服务端算法。
 *
 * <p>替代旧 12 回合脚本，实现开放式轮转：发牌 → 摸打 → 吃碰杠胡裁决 → 胡/流局。
 * 牌墙沿用确定性 seed（136 无花牌），庄家固定 1 号位、庄家起手 14 张/闲家 13 张、
 * 裁决优先级胡>杠>碰>吃、计分按台州大众玩法文档。QA 模式写入 qaDisclosure；
 * 生产 START_ROUND 使用 SERVER_AUTHORITY 模式，标明南北娱乐自研服务端规则。
 */
final class QaTaizhouRoundEngine {
    static final int BOT_POOL_SIZE = 300;
    /** 自建：庄家固定 1 号位。 */
    private static final int DEALER_SEAT = 1;
    private static final int MAX_LOOP_STEPS = 4096;

    private final QaRoundStateCodec codec;
    private final QaRoundEventFactory eventFactory;
    private final QaRoundTurnDriver turnDriver;
    private final QaRoundCommandApplier commandApplier;
    private final QaRoundFlowAdvance flowAdvance;

    QaTaizhouRoundEngine(ObjectMapper objectMapper) {
        this(objectMapper, TaizhouRoundMode.QA);
    }

    QaTaizhouRoundEngine(ObjectMapper objectMapper, TaizhouRoundMode mode) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(mode, "mode");
        QaTaizhouProjection projection = new QaTaizhouProjection(objectMapper);
        this.codec = new QaRoundStateCodec(objectMapper, projection, mode);
        this.eventFactory = new QaRoundEventFactory(projection, mode);
        this.turnDriver =
                new QaRoundTurnDriver(eventFactory, new QaTaizhouBotPolicy(), new QaTingInfoCalculator());
        this.commandApplier = new QaRoundCommandApplier();
        this.flowAdvance = new QaRoundFlowAdvance(eventFactory, turnDriver);
    }

    /** 从会话 state JSON 读回牌桌状态（要求 qaRound 节点存在）。 */
    QaRoundTable readTable(JsonNode sessionState) {
        return codec.readTable(sessionState);
    }

    /** 生成写入会话 state 的完整快照 JSON。 */
    JsonNode sessionState(QaRoundTable table, QaRoundContext context) {
        return codec.sessionState(table, context);
    }

    QaTaizhouRoundResult start(Request request) {
        return start(request, null);
    }

    /** wallOverride 仅供单元测试注入确定性牌墙。 */
    QaTaizhouRoundResult start(Request request, List<Integer> wallOverride) {
        request.validate();
        long nextRevision = request.expectedRevision() + 1L;
        int roundNumber = request.currentRoundNumber() + 1;
        QaRoundContext context = request.context();
        Set<Integer> botSeats = new HashSet<>();
        for (QaMahjongAutoRoundEngine.SeatInput seat : request.seats()) {
            if (seat.qaBot()) {
                botSeats.add(seat.seatNumber());
            }
        }
        List<Integer> wall =
                wallOverride != null
                        ? new ArrayList<>(wallOverride)
                        : QaTaizhouTiles.buildWall(
                                QaTaizhouTiles.seed(
                                        request.roomNumber(),
                                        request.expectedRevision(),
                                        request.currentRoundNumber(),
                                        request.seats().stream()
                                                .map(QaMahjongAutoRoundEngine.SeatInput::userId)
                                                .toList()));
        List<GameEvent> events = new ArrayList<>();
        QaRoundTable table =
                flowAdvance.openRound(
                        context, roundNumber, DEALER_SEAT, botSeats, wall, nextRevision, events);
        advance(table, context, nextRevision, events);
        JsonNode state = codec.sessionState(table, context);
        return new QaTaizhouRoundResult(
                phaseOf(table), roundNumber, nextRevision, state, events, deltas(table), table);
    }

    QaRoundStep apply(
            QaRoundTable table,
            QaRoundContext context,
            int actorSeat,
            GameplayCommandType type,
            JsonNode payload,
            long nextRevision) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(context, "context");
        if (actorSeat < 1 || actorSeat > table.chairCount) {
            throw new ApiException(ErrorCode.GAMEPLAY_FORBIDDEN, "无权操作该座位");
        }
        if (type == GameplayCommandType.NEXT_ROUND) {
            return flowAdvance.nextRound(this, table, context, nextRevision);
        }
        if (table.outcome != null) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "本局已结束");
        }
        List<GameEvent> events = new ArrayList<>();
        commandApplier.apply(turnDriver, table, context, actorSeat, type, payload, nextRevision, events);
        if (type == GameplayCommandType.MULTIPLE_CHOICE
                && table.stage == QaRoundTable.Stage.AWAIT_MULTIPLE
                && QaRoundFlowAdvance.allMultipleChoicesMade(context, table)) {
            flowAdvance.dealAfterMultipleChoice(context, table, nextRevision, events);
        }
        advance(table, context, nextRevision, events);
        return new QaRoundStep(events, deltas(table), table.outcome != null, table);
    }

    /** 假人驱动事件循环：同步推进到下一个真人动作点或局终（在命令事务内完成）。 */
    void advance(
            QaRoundTable table, QaRoundContext context, long revision, List<GameEvent> events) {
        int guard = 0;
        while (table.outcome == null && !table.hasUnansweredHumanOffer()) {
            if (++guard > MAX_LOOP_STEPS) {
                throw new IllegalStateException("QA round loop did not converge");
            }
            switch (table.stage) {
                case AWAIT_MULTIPLE -> {
                    return;
                }
                case AWAIT_DRAW -> turnDriver.beginTurn(table, context, revision, events);
                case AWAIT_CLAIMS -> turnDriver.adjudicate(table, context, revision, events);
                case AWAIT_PLAY ->
                        turnDriver.offerOrBotPlay(table, context, revision, events, null);
                case ROUND_OVER -> {
                    return;
                }
            }
        }
    }

    static GamePhase phaseOf(QaRoundTable table) {
        if (table.outcome != null) {
            return GamePhase.ROUND_RESULT;
        }
        return table.stage == QaRoundTable.Stage.AWAIT_MULTIPLE
                ? GamePhase.DEALING
                : GamePhase.PLAYING;
    }

    static Map<Integer, Long> deltas(QaRoundTable table) {
        return table.outcome == null ? Map.of() : table.outcome.deltas();
    }

    record Request(
            long gameId,
            String roomNumber,
            int chairCount,
            int maxPlayCount,
            String gameRuleDisplay,
            long expectedRevision,
            int currentRoundNumber,
            List<QaMahjongAutoRoundEngine.SeatInput> seats,
            Instant occurredAt) {
        Request {
            seats = List.copyOf(seats);
        }

        void validate() {
            if (gameId <= 0 || expectedRevision < 0 || currentRoundNumber < 0) {
                throw new IllegalArgumentException("invalid QA round cursor");
            }
            if (roomNumber == null || !roomNumber.matches("\\d{6}")) {
                throw new IllegalArgumentException("invalid roomNumber");
            }
            if (chairCount != seats.size() || (chairCount != 2 && chairCount != 4)) {
                throw new IllegalArgumentException(
                        "QA round requires a full two- or four-seat table");
            }
            if (maxPlayCount <= 0 || gameRuleDisplay == null || gameRuleDisplay.isBlank()) {
                throw new IllegalArgumentException("invalid QA table metadata");
            }
            Set<Integer> seenSeats = new HashSet<>();
            for (QaMahjongAutoRoundEngine.SeatInput seat : seats) {
                if (!seenSeats.add(seat.seatNumber())
                        || seat.seatNumber() < 1
                        || seat.seatNumber() > chairCount) {
                    throw new IllegalArgumentException("invalid QA seat order");
                }
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }

        QaRoundContext context() {
            return new QaRoundContext(roomNumber, gameRuleDisplay, seats, occurredAt);
        }
    }
}
