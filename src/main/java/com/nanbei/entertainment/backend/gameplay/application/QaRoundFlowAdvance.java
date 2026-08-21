package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * QA 回合开局流程（南北自建规则，非原版服务端算法）：首局与 NEXT_ROUND 共用的
     * 补位/洗牌/加配/发牌链路，以及生牌数、剩余庄数的自建计数。
 *
 * <p>生牌数对齐原版 {@code msgShengPaiCnt}(1049) 语义但数值为自建：开局固定 22，
 * 每从墙摸走一张 -1，归零不再变化。剩余庄数对齐 {@code msgLeftBanker}(1050) 语义但
 * 数值为自建：总盘固定 8 局，每局开始下发 {@code 8 - roundNumber + 1}。
 * NEXT_ROUND 的"任何座位成员可发起"是南北自建简化，原版多局流转无服务端证据。
 */
final class QaRoundFlowAdvance {
    /** 自建：开局生牌数（对齐 TableInfo 展示语义，非原版服务端下发值）。 */
    static final int INITIAL_SHENG_PAI_COUNT = 22;
    /** 自建：总盘局数（对齐原版房间 maxPlayCount 8 的默认档）。 */
    static final int TOTAL_BANKER_ROUNDS = 8;

    private final QaRoundEventFactory eventFactory;
    private final QaRoundTurnDriver turnDriver;

    QaRoundFlowAdvance(QaRoundEventFactory eventFactory, QaRoundTurnDriver turnDriver) {
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
        this.turnDriver = Objects.requireNonNull(turnDriver, "turnDriver");
    }

    /**
     * 开一局新桌：补位广播 → 洗牌 → 加倍选择。真人桌停在加倍阶段，等
     * {@code MULTIPLE_CHOICE} 命令收齐后再发牌；全假人桌会同步继续推进。
     */
    QaRoundTable openRound(
            QaRoundContext context,
            int roundNumber,
            int dealerSeat,
            Set<Integer> botSeats,
            List<Integer> wall,
            long revision,
            List<GameEvent> events) {
        QaRoundTable table =
                QaRoundTable.newRound(context.chairCount(), dealerSeat, roundNumber, botSeats);
        table.shengPaiCount = INITIAL_SHENG_PAI_COUNT;
        table.leftBankerCount = Math.max(0, TOTAL_BANKER_ROUNDS - roundNumber + 1);
        int wallSize = wall.size();
        table.wall.addAll(wall);
        table.stage = QaRoundTable.Stage.AWAIT_MULTIPLE;
        for (int seat : botSeats) {
            table.choices().put(seat, "NONE");
        }
        if (!botSeats.isEmpty()) {
            events.add(eventFactory.botSeatsFilled(revision, context, QaTaizhouRoundEngine.BOT_POOL_SIZE));
        }
        events.add(eventFactory.wallShuffled(revision, wallSize, table.wall.size()));
        events.add(eventFactory.multipleChoiceStarted(revision, context, table));
        if (allMultipleChoicesMade(context, table)) {
            dealAfterMultipleChoice(context, table, revision, events);
        }
        return table;
    }

    /**
     * 加倍选择完成后继续开局：掷骰 → LEFT_BANKER → 发牌 → 首个 SHENG_PAI_COUNT → 庄家出牌权。
     * 掷骰字段对齐原版 msgThrowChip 形状；数值为南北自建确定性派生。
     * 首个 SHENG_PAI_COUNT 在发牌后发出，值为 22；起手发牌不扣生牌数。
     */
    void dealAfterMultipleChoice(
            QaRoundContext context, QaRoundTable table, long revision, List<GameEvent> events) {
        if (table.stage != QaRoundTable.Stage.AWAIT_MULTIPLE) {
            throw new IllegalStateException("table is not waiting for multiple choices");
        }
        table.diceRoll = diceRoll(context, table);
        events.add(eventFactory.diceRolled(revision, table));
        // 台州大众玩法：庄家起手 14 张、闲家 13 张；起手发牌不扣生牌数。
        for (int deal = 0; deal < 13; deal++) {
            for (int seat = 1; seat <= context.chairCount(); seat++) {
                table.hands().get(seat).add(table.drawFromWall());
            }
        }
        int dealerTile = table.drawFromWall();
        table.hands().get(table.dealerSeat).add(dealerTile);
        table.stage = QaRoundTable.Stage.AWAIT_PLAY;
        events.add(eventFactory.leftBanker(revision, table.leftBankerCount));
        events.addAll(eventFactory.dealt(revision, context, table));
        table.diceRoll = null;
        events.add(eventFactory.shengPaiCount(revision, table.shengPaiCount));
        if (table.isBot(table.dealerSeat)) {
            events.add(eventFactory.turnAdvanced(revision, table));
        }
        turnDriver.offerOrBotPlay(table, context, revision, events, dealerTile);
    }

    /**
     * NEXT_ROUND（自建多局流转）：上一局 ROUND_RESULT 后用新确定性 seed（房间号 +
     * 当前修订 + 上一局局数 + 座位用户）派生新墙，roundNumber+1 重开一局；比分由
     * 会话座位累计。返回的 {@link QaRoundStep#table()} 是新牌桌，旧桌引用作废。
     */
    QaRoundStep nextRound(
            QaTaizhouRoundEngine engine,
            QaRoundTable previous,
            QaRoundContext context,
            long revision) {
        if (previous.outcome == null) {
            throw new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, "当前牌局尚未结束");
        }
        List<Integer> wall =
                QaTaizhouTiles.buildWall(
                        QaTaizhouTiles.seed(
                                context.roomNumber(),
                                revision - 1,
                                previous.roundNumber,
                                context.seats().stream()
                                        .map(QaMahjongAutoRoundEngine.SeatInput::userId)
                                        .toList()));
        List<GameEvent> events = new ArrayList<>();
        QaRoundTable table =
                openRound(
                        context,
                        previous.roundNumber + 1,
                        previous.dealerSeat,
                        previous.botSeats,
                        wall,
                        revision,
                        events);
        engine.advance(table, context, revision, events);
        return new QaRoundStep(
                events,
                QaTaizhouRoundEngine.deltas(table),
                table.outcome != null,
                table);
    }

    static boolean allMultipleChoicesMade(QaRoundContext context, QaRoundTable table) {
        for (QaMahjongAutoRoundEngine.SeatInput seat : context.seats()) {
            if (!table.choices().containsKey(seat.seatNumber())) {
                return false;
            }
        }
        return true;
    }

    private static QaRoundTable.DiceRoll diceRoll(QaRoundContext context, QaRoundTable table) {
        int first =
                Math.floorMod(
                                Objects.hash(
                                        context.roomNumber(),
                                        table.roundNumber,
                                        table.dealerSeat,
                                        table.wall.size()),
                                6)
                        + 1;
        int second =
                Math.floorMod(
                                Objects.hash(
                                        context.roomNumber(),
                                        table.roundNumber,
                                        table.dealerSeat,
                                        table.wall.hashCode()),
                                6)
                        + 1;
        return new QaRoundTable.DiceRoll(table.dealerSeat, List.of(first, second), 4, true);
    }
}
