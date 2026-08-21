package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * QA 台州回合的可变牌桌状态（南北自建测试模型，非原版服务端算法）。
 * 状态经 {@link QaRoundStateCodec} 序列化进 {@code game_sessions.state}，
 * 每条真人命令读回并续推。
 */
final class QaRoundTable {
    enum Stage {
        /** 开局加倍选择中；停在此阶段直到全部真人/假人都有选择。 */
        AWAIT_MULTIPLE,
        /** 等待 activeSeat 摸牌（引擎内部瞬时态，不会停在真人面前）。 */
        AWAIT_DRAW,
        /** activeSeat 已摸牌/副露后等待出牌权限裁决。 */
        AWAIT_PLAY,
        /** 弃牌窗口打开，等待真人吃碰杠胡/过。 */
        AWAIT_CLAIMS,
        ROUND_OVER
    }

    final int chairCount;
    final int dealerSeat;
    final int roundNumber;
    final int maxPlayCount;
    Stage stage = Stage.AWAIT_DRAW;
    int turnIndex;
    int activeSeat;
    final List<Integer> wall = new ArrayList<>();
    private final List<Integer> openTiles = new ArrayList<>();
    private final Map<Integer, List<Integer>> hands = new LinkedHashMap<>();
    private final Map<Integer, List<Integer>> rivers = new LinkedHashMap<>();
    private final Map<Integer, List<Integer>> flowers = new LinkedHashMap<>();
    private final Map<Integer, List<Meld>> melds = new LinkedHashMap<>();
    LastDiscard lastDiscard;
    /** 仍有效的真人 offer（seat → offer）；PASS/消费/过期后即移除。 */
    private final Map<Integer, PendingOffer> offers = new LinkedHashMap<>();
    final Set<Integer> botSeats;
    private final Map<Integer, String> choices = new LinkedHashMap<>();
    DiceRoll diceRoll;
    QaTaizhouJokerRule jokerRule = QaTaizhouJokerRule.unrevealed();
    int drawnTileSeat;
    Integer drawnTile;
    int nextOfferId = 1;
    Outcome outcome;
    QaTaizhouTotalResult totalResult;
    /**
     * 生牌数（南北自建 QA 规则，非原版服务端算法）：开局固定 22，真人/假人每从墙摸走
     * 一张牌 -1，归零后不再变化；发牌不扣减。-1 表示旧状态缺席（快照下发 null）。
     */
    int shengPaiCount = -1;
    /**
     * 剩余庄数（南北自建 QA 规则，非原版服务端算法）：总盘固定 8 局，
     * 每局开始下发 {@code 8 - roundNumber + 1}。-1 表示旧状态缺席（快照下发 null）。
     */
    int leftBankerCount = -1;
    /** 真人座位最近一次出牌 offer 的听牌映射（自建）；出牌/局终时清除。 */
    private final Map<Integer, List<TingEntry>> tingInfos = new LinkedHashMap<>();

    record Meld(String combType, List<Integer> tiles, int fromSeat) {}

    record LastDiscard(int seat, int tile, int tileIndex) {}

    /** Original-shaped msgThrowChip projection persisted only while the dice bridge is pending. */
    record DiceRoll(int seatNumber, List<Integer> values, int gameStep, boolean showAni) {
        DiceRoll {
            values = List.copyOf(values);
            if (seatNumber <= 0 || seatNumber > 4) {
                throw new IllegalArgumentException("dice seat is outside chair count");
            }
            if (values.isEmpty() || values.size() > 3) {
                throw new IllegalArgumentException("dice values must contain one to three chips");
            }
            for (int value : values) {
                if (value < 1 || value > 6) {
                    throw new IllegalArgumentException("dice value must be between 1 and 6");
                }
            }
            if (gameStep != 4 && gameStep != 5 && gameStep != 7) {
                throw new IllegalArgumentException("dice game step must be a throw-chip step");
            }
        }
    }

    /** TING_INFO 单条映射（自建）：打出 discard 后可听 huTargets（对齐 msgTingMahInfo 语义）。 */
    record TingEntry(int discard, List<Integer> huTargets) {
        TingEntry {
            huTargets = List.copyOf(huTargets);
        }
    }

    record Outcome(
            int winnerSeat,
            String winType,
            Integer discarderSeat,
            Integer winningTile,
            Map<Integer, Long> deltas,
            Map<Integer, String> endStates) {
        Outcome(
                int winnerSeat,
                String winType,
                Integer discarderSeat,
                Map<Integer, Long> deltas,
                Map<Integer, String> endStates) {
            this(winnerSeat, winType, discarderSeat, null, deltas, endStates);
        }
    }

    /** 发给真人座位的一次性动作权限；actionToken 消费后不可重放。 */
    static final class PendingOffer {
        final int offerId;
        final String actionToken;
        final int powerMask;
        final Integer contextTile;
        final List<List<Integer>> chowCandidates;
        final List<QaMeldCandidates.KongOption> kongOptions;
        final int fromSeat;
        final boolean playOffer;
        QaClaim.Kind claimKind;
        Integer candidateIndex;
        boolean passed;

        /**
         * offer 发出的时刻，用于服务端超时裁决。
         *
         * <p>原版服务端的超时行为不在客户端归档内，这是南北自建实现：见
         * {@code QaRoundTurnDriver#expireTimedOutOffers}。老状态没有该字段时为 null，
         * 视为「不超时」，避免升级瞬间把在场 offer 全部判掉。
         */
        Long offeredAtEpochMilli;

        PendingOffer(
                int offerId,
                String actionToken,
                int powerMask,
                Integer contextTile,
                List<List<Integer>> chowCandidates,
                List<QaMeldCandidates.KongOption> kongOptions,
                int fromSeat,
                boolean playOffer) {
            this.offerId = offerId;
            this.actionToken = actionToken;
            this.powerMask = powerMask;
            this.contextTile = contextTile;
            this.chowCandidates = chowCandidates;
            this.kongOptions = kongOptions;
            this.fromSeat = fromSeat;
            this.playOffer = playOffer;
        }

        boolean answered() {
            return claimKind != null || passed;
        }
    }

    private QaRoundTable(
            int chairCount,
            int dealerSeat,
            int roundNumber,
            int maxPlayCount,
            Collection<Integer> botSeats) {
        this.chairCount = chairCount;
        this.dealerSeat = dealerSeat;
        this.roundNumber = roundNumber;
        this.maxPlayCount = maxPlayCount;
        this.activeSeat = dealerSeat;
        this.botSeats = new LinkedHashSet<>(botSeats);
        this.totalResult = QaTaizhouTotalResult.empty(chairCount);
    }

    static QaRoundTable newRound(
            int chairCount, int dealerSeat, int roundNumber, Collection<Integer> botSeats) {
        return newRound(chairCount, dealerSeat, roundNumber, 8, botSeats);
    }

    static QaRoundTable newRound(
            int chairCount,
            int dealerSeat,
            int roundNumber,
            int maxPlayCount,
            Collection<Integer> botSeats) {
        QaRoundTable table =
                new QaRoundTable(chairCount, dealerSeat, roundNumber, maxPlayCount, botSeats);
        for (int seat = 1; seat <= chairCount; seat++) {
            table.hands.put(seat, new ArrayList<>());
            table.rivers.put(seat, new ArrayList<>());
            table.flowers.put(seat, new ArrayList<>());
            table.melds.put(seat, new ArrayList<>());
        }
        return table;
    }

    Map<Integer, List<Integer>> hands() {
        return hands;
    }

    Map<Integer, List<Integer>> rivers() {
        return rivers;
    }

    Map<Integer, List<Integer>> flowers() {
        return flowers;
    }

    Map<Integer, List<Meld>> melds() {
        return melds;
    }

    List<Integer> openTiles() {
        return openTiles;
    }

    Map<Integer, PendingOffer> offers() {
        return offers;
    }

    Map<Integer, String> choices() {
        return choices;
    }

    Map<Integer, List<TingEntry>> tingInfos() {
        return tingInfos;
    }

    int nextSeat(int seat) {
        return seat % chairCount + 1;
    }

    boolean isBot(int seat) {
        return botSeats.contains(seat);
    }

    /** 是否存在尚未作答的真人 offer（引擎事件循环的停止条件）。 */
    boolean hasUnansweredHumanOffer() {
        for (Map.Entry<Integer, PendingOffer> entry : offers.entrySet()) {
            if (!isBot(entry.getKey()) && !entry.getValue().answered()) {
                return true;
            }
        }
        return false;
    }

    /** 当前弃牌窗口里所有已声明的真人动作。 */
    List<QaClaim> pendingClaims() {
        List<QaClaim> claims = new ArrayList<>();
        for (PendingOffer offer : offers.values()) {
            if (offer.claimKind != null) {
                claims.add(new QaClaim(seatOf(offer), offer.claimKind));
            }
        }
        return claims;
    }

    private int seatOf(PendingOffer target) {
        for (Map.Entry<Integer, PendingOffer> entry : offers.entrySet()) {
            if (entry.getValue() == target) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("offer is not attached to any seat");
    }

    /**
     * 黄牌保留张数（南北自建，依据 {@code 创建房间的台州麻将游戏规则.md}：「当剩下牌的总数等于
     * 8 对时…如果还是没有人和牌…强制结束，庄家自动下庄」）。
     *
     * <p>这 16 张是不可摸的王牌：局内摸牌降到该阈值即黄牌收局，所以「剩余」停在 16 而不是 0。
     * 原始服务端的王牌张数与边界仍属 {@code UNRESOLVED_ORIGINAL_SERVER}，这里只按已知规则文档实现。
     */
    static final int DRAW_RESERVE_TILES = 16;

    /** 局内摸牌是否已到黄牌阈值；发牌与翻得不走这条判断。 */
    boolean wallExhausted() {
        return wall.size() <= DRAW_RESERVE_TILES;
    }

    int drawFromWall() {
        if (wall.isEmpty()) {
            throw new IllegalStateException("QA wall exhausted");
        }
        return wall.remove(0);
    }

    void markDrawnTile(int seat, int tile) {
        if (seat < 1 || seat > chairCount || !hands.get(seat).contains(tile)) {
            throw new IllegalArgumentException("drawn tile must belong to its seat");
        }
        drawnTileSeat = seat;
        drawnTile = tile;
    }

    void clearDrawnTile() {
        drawnTileSeat = 0;
        drawnTile = null;
    }

    boolean hasDrawnTile(int seat) {
        return drawnTile != null && drawnTileSeat == seat;
    }

    /** 翻开牌墙中的一张牌并建立本局财神/白板替代映射。 */
    void revealJoker() {
        int openTile = drawFromWall();
        if (!QaTaizhouTiles.isPlayable(openTile)) {
            throw new IllegalStateException("opened wall tile is not playable: " + openTile);
        }
        openTiles.add(openTile);
        jokerRule = QaTaizhouJokerRule.fromOpenTile(openTile);
    }
}
