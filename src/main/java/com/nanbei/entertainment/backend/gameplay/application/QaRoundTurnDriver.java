package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * QA 轮转驱动（南北自建规则，非原版服务端算法）：
 * 摸牌、假人摸打、弃牌窗口、裁决与胡/流局收尾都在这里。
 */
final class QaRoundTurnDriver {
    private final QaRoundEventFactory eventFactory;
    private final QaTaizhouBotPolicy botPolicy;
    private final QaTingInfoCalculator tingInfoCalculator;

    QaRoundTurnDriver(
            QaRoundEventFactory eventFactory,
            QaTaizhouBotPolicy botPolicy,
            QaTingInfoCalculator tingInfoCalculator) {
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
        this.botPolicy = Objects.requireNonNull(botPolicy, "botPolicy");
        this.tingInfoCalculator = Objects.requireNonNull(tingInfoCalculator, "tingInfoCalculator");
    }

    /** activeSeat 摸一张，随后假人自动摸打、真人收到出牌 offer。 */
    void beginTurn(
            QaRoundTable table, QaRoundContext context, long revision, List<GameEvent> events) {
        table.turnIndex++;
        drawAndOffer(table, context, revision, events, table.activeSeat);
    }

    /**
     * 开局补花（南北自建 QA 规则）：发牌后从 1 号位起依次把手里的花牌移入花区并补摸，
     * 补到的花牌继续补，事件链与摸牌补花一致；墙尽按流局处理。
     */
    void replaceDealtFlowers(
            QaRoundTable table, QaRoundContext context, long revision, List<GameEvent> events) {
        for (int seat = 1; seat <= table.chairCount; seat++) {
            List<Integer> hand = table.hands().get(seat);
            Integer flower = takeFirstFlower(hand);
            while (flower != null) {
                table.flowers().get(seat).add(flower);
                if (table.wallExhausted()) {
                    declareDraw(table, context, revision, events);
                    return;
                }
                int replacement = drawCounted(table);
                events.add(eventFactory.flowerReplaced(revision, seat, flower, replacement));
                if (QaTaizhouTiles.isWallFlower(replacement)) {
                    flower = replacement;
                } else {
                    hand.add(replacement);
                    flower = takeFirstFlower(hand);
                }
            }
        }
    }

    private static Integer takeFirstFlower(List<Integer> hand) {
        for (int index = 0; index < hand.size(); index++) {
            int tile = hand.get(index);
            if (QaTaizhouTiles.isWallFlower(tile)) {
                hand.remove(index);
                return tile;
            }
        }
        return null;
    }

    /** 杠后补摸（自建：从墙头补），随后同上。 */
    void replacementDrawAndOffer(
            QaRoundTable table, QaRoundContext context, long revision, List<GameEvent> events) {
        drawAndOffer(table, context, revision, events, table.activeSeat);
    }

    private void drawAndOffer(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            int seat) {
        int drawnTile = drawWithFlowerReplacement(table, revision, events, seat);
        if (drawnTile < 0) {
            declareDraw(table, context, revision, events);
            return;
        }
        table.hands().get(seat).add(drawnTile);
        table.markDrawnTile(seat, drawnTile);
        events.addAll(eventFactory.drawn(revision, context, table, seat));
        table.stage = QaRoundTable.Stage.AWAIT_PLAY;
        offerOrBotPlay(table, context, revision, events, drawnTile);
    }

    /**
     * AWAIT_PLAY 的落点：假人在服务端同步决策、客户端按 playbackDelayMillis 展示；
     * 真人收到带 actionToken 的出牌 offer（自建：含自摸胡与暗杠/补杠位）。
     */
    void offerOrBotPlay(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            Integer contextTile) {
        int seat = table.activeSeat;
        List<Integer> hand = table.hands().get(seat);
        if (table.isBot(seat)) {
            if (botPolicy.wantsSelfDrawWin(hand, table.jokerRule)) {
                declareWin(
                        table,
                        context,
                        revision,
                        events,
                        seat,
                        "ZIMO",
                        null,
                        contextTile == null ? QaTaizhouTiles.NO_TILE : contextTile);
                return;
            }
            discard(
                    table,
                    context,
                    revision,
                    events,
                    seat,
                    botPolicy.discardChoice(hand, table.jokerRule));
            return;
        }
        int mask = QaPowerMask.PLAY;
        if (QaWinDetector.canWin(hand, table.jokerRule)) {
            mask |= QaPowerMask.HU;
        }
        List<QaMeldCandidates.KongOption> kongOptions =
                QaMeldCandidates.ownDrawKongOptions(
                        hand, 0, pongMelds(table, seat), table.jokerRule);
        for (QaMeldCandidates.KongOption option : kongOptions) {
            mask |= option.kongType().equals("CONCEALED") ? QaPowerMask.CKONG : QaPowerMask.TKONG;
        }
        QaRoundTable.PendingOffer offer =
                new QaRoundTable.PendingOffer(
                        table.nextOfferId++,
                        UUID.randomUUID().toString(),
                        mask,
                        contextTile,
                        List.of(),
                        kongOptions,
                        seat,
                        true);
        offer.offeredAtEpochMilli = context.occurredAt().toEpochMilli();
        table.offers().put(seat, offer);
        events.add(eventFactory.actionOffered(revision, seat, offer));
        appendTingInfo(table, revision, events, seat);
    }

    /**
     * TING_INFO（自建，对齐 msgTingMahInfo 语义）：出牌权窗口开启时为真人座位计算
     * "打出每张手牌后听哪些牌"的映射并下发；无听或计算降级时发空数组。
     */
    private void appendTingInfo(
            QaRoundTable table, long revision, List<GameEvent> events, int seat) {
        List<QaRoundTable.TingEntry> entries =
                tingInfoCalculator.compute(
                        table.hands().get(seat), table.jokerRule, 50_000_000L);
        table.tingInfos().put(seat, entries);
        events.add(eventFactory.tingInfo(revision, seat, entries));
    }

    /** 出牌（大众玩法允许打出财神；花牌不会进入手牌），随后打开弃牌窗口。 */
    void discard(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
        int seat,
        int tile) {
        List<Integer> hand = table.hands().get(seat);
        if (!hand.remove(Integer.valueOf(tile))) {
            throw new IllegalStateException("discard tile is not in hand");
        }
        table.clearDrawnTile();
        List<Integer> river = table.rivers().get(seat);
        river.add(tile);
        table.tingInfos().remove(seat);
        table.lastDiscard = new QaRoundTable.LastDiscard(seat, tile, river.size() - 1);
        events.addAll(eventFactory.discarded(revision, context, table, seat));
        table.stage = QaRoundTable.Stage.AWAIT_CLAIMS;
        openClaimWindow(table, context, revision, events, seat, tile);
    }

    /**
     * 弃牌窗口（自建）：假人即时决策，能胡立即截胡（不再发真人 offer）；
     * 真人按候选拿到 ACTION_OFFERED（吃仅限出牌者下家）。
     */
    private void openClaimWindow(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            int discarder,
            int tile) {
        int botWinner = -1;
        for (int seat = 1; seat <= table.chairCount; seat++) {
            if (seat != discarder
                    && table.isBot(seat)
                    && botPolicy.wantsClaimWin(
                            table.hands().get(seat), tile, table.jokerRule)) {
                if (botWinner < 0
                        || Math.floorMod(seat - discarder, table.chairCount)
                                < Math.floorMod(botWinner - discarder, table.chairCount)) {
                    botWinner = seat;
                }
            }
        }
        if (botWinner > 0) {
            declareWin(table, context, revision, events, botWinner, "DIANPAO", discarder, tile);
            return;
        }
        int chowSeat = table.nextSeat(discarder);
        for (int seat = 1; seat <= table.chairCount; seat++) {
            if (seat == discarder || table.isBot(seat)) {
                continue;
            }
            List<Integer> hand = table.hands().get(seat);
            int mask = QaPowerMask.NONE;
            if (QaWinDetector.canWin(append(hand, tile), table.jokerRule)) {
                mask |= QaPowerMask.HU;
            }
            List<QaMeldCandidates.KongOption> kongOptions = List.of();
            if (QaMeldCandidates.canExposedKong(hand, tile, table.jokerRule)) {
                mask |= QaPowerMask.MKONG;
                kongOptions = List.of(new QaMeldCandidates.KongOption("EXPOSED", tile));
            } else if (QaMeldCandidates.canPung(hand, tile, table.jokerRule)) {
                mask |= QaPowerMask.PUNG;
            }
            List<List<Integer>> chowCandidates = List.of();
            if (seat == chowSeat) {
                chowCandidates =
                        QaMeldCandidates.chowCandidates(hand, tile, table.jokerRule);
                if (!chowCandidates.isEmpty()) {
                    mask |= QaPowerMask.CHOW;
                }
            }
            if (mask == QaPowerMask.NONE) {
                continue;
            }
            mask |= QaPowerMask.CANCEL;
            QaRoundTable.PendingOffer offer =
                    new QaRoundTable.PendingOffer(
                            table.nextOfferId++,
                            UUID.randomUUID().toString(),
                            mask,
                            tile,
                            chowCandidates,
                            kongOptions,
                            discarder,
                            false);
            offer.offeredAtEpochMilli = context.occurredAt().toEpochMilli();
            table.offers().put(seat, offer);
            events.add(eventFactory.actionOffered(revision, seat, offer));
        }
    }

    /** 真人全部作答后裁决（自建优先级 胡>杠>碰>吃），无人声明则轮转下家。 */
    void adjudicate(
            QaRoundTable table, QaRoundContext context, long revision, List<GameEvent> events) {
        int discarder = table.lastDiscard.seat();
        QaClaim winner = QaAdjudicator.choose(table.pendingClaims(), discarder, table.chairCount);
        for (Map.Entry<Integer, QaRoundTable.PendingOffer> entry : table.offers().entrySet()) {
            QaRoundTable.PendingOffer offer = entry.getValue();
            if (offer.passed) {
                continue; // PASS 时已经发过 ACTION_EXPIRED
            }
            if (winner != null && entry.getKey() == winner.seat()) {
                continue;
            }
            events.add(eventFactory.actionExpired(revision, entry.getKey(), offer.offerId));
        }
        Integer chowCandidateIndex =
                winner == null ? null : table.offers().get(winner.seat()).candidateIndex;
        table.offers().clear();
        if (winner == null) {
            table.activeSeat = table.nextSeat(discarder);
            events.add(eventFactory.turnAdvanced(revision, table));
            table.stage = QaRoundTable.Stage.AWAIT_DRAW;
            return;
        }
        applyClaim(
                table, context, revision, events, winner, discarder, chowCandidateIndex);
    }

    private void applyClaim(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            QaClaim winner,
            int discarder,
            Integer chowCandidateIndex) {
        int tile = table.lastDiscard.tile();
        if (winner.kind() == QaClaim.Kind.HU) {
            declareWin(
                    table,
                    context,
                    revision,
                    events,
                    winner.seat(),
                    "DIANPAO",
                    discarder,
                    tile);
            return;
        }
        List<Integer> hand = table.hands().get(winner.seat());
        QaRoundTable.Meld meld;
        switch (winner.kind()) {
            case CHOW -> {
                List<Integer> tiles =
                        chowCandidateIndex == null
                                ? List.of(tile, tile, tile)
                                : QaMeldCandidates.chowCandidates(
                                                hand, tile, table.jokerRule)
                                        .get(chowCandidateIndex);
                for (int member : tiles) {
                    if (member != tile) {
                        hand.remove(Integer.valueOf(member));
                    }
                }
                meld = new QaRoundTable.Meld("CHOW", tiles, discarder);
            }
            case PUNG -> {
                hand.remove(Integer.valueOf(tile));
                hand.remove(Integer.valueOf(tile));
                meld = new QaRoundTable.Meld("PONG", List.of(tile, tile, tile), discarder);
            }
            case KONG -> {
                hand.remove(Integer.valueOf(tile));
                hand.remove(Integer.valueOf(tile));
                hand.remove(Integer.valueOf(tile));
                meld =
                        new QaRoundTable.Meld(
                                "EXPOSED_KONG", List.of(tile, tile, tile, tile), discarder);
            }
            default -> throw new IllegalStateException("unexpected claim " + winner.kind());
        }
        removeLastRiverTile(table, discarder);
        table.melds().get(winner.seat()).add(meld);
        table.activeSeat = winner.seat();
        events.add(eventFactory.meldApplied(revision, winner.seat(), meld));
        if (winner.kind() == QaClaim.Kind.KONG) {
            table.stage = QaRoundTable.Stage.AWAIT_DRAW;
            replacementDrawAndOffer(table, context, revision, events);
            return;
        }
        table.stage = QaRoundTable.Stage.AWAIT_PLAY;
        offerOrBotPlay(table, context, revision, events, tile);
    }

    /** 真人暗杠/补杠：MELD_APPLIED 后补摸一张并重新开出牌 offer。 */
    void applyOwnKong(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            int seat,
            QaRoundTable.Meld meld) {
        events.add(eventFactory.meldApplied(revision, seat, meld));
        replacementDrawAndOffer(table, context, revision, events);
    }

    /** PASS 或裁决后关闭 offer（南北自建 QA 事件）。 */
    void expireOffer(
            QaRoundTable table, long revision, List<GameEvent> events, int seat, int offerId) {
        events.add(eventFactory.actionExpired(revision, seat, offerId));
    }

    /** 加配选择变更广播（沿用 MULTIPLE_CHOICE_STARTED 的 payload 形状）。 */
    void multipleChoiceChanged(
            QaRoundTable table, QaRoundContext context, long revision, List<GameEvent> events) {
        events.add(eventFactory.multipleChoiceChanged(revision, context, table));
    }

    /** 被吃碰杠的弃牌从河移除（南北自建 QA 规则）。 */
    private void removeLastRiverTile(QaRoundTable table, int discarder) {
        List<Integer> river = table.rivers().get(discarder);
        river.remove(river.size() - 1);
        table.lastDiscard = null;
    }

    void declareWin(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            int winnerSeat,
            String winType,
            Integer discarderSeat,
            int winningTile) {
        QaTaizhouScorer.RoundScore score =
                QaTaizhouScorer.score(
                        table, winnerSeat, winType, discarderSeat, winningTile);
        Map<Integer, String> endStates = new LinkedHashMap<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            boolean winner = seat == winnerSeat;
            if (winner) {
                endStates.put(seat, "EPS_HU");
            } else if (discarderSeat != null && seat == discarderSeat) {
                endStates.put(seat, "EPS_DISCARD");
            } else {
                endStates.put(seat, "EPS_NULL");
            }
        }
        table.outcome =
                new QaRoundTable.Outcome(
                        winnerSeat,
                        winType,
                        discarderSeat,
                        winningTile,
                        score.deltas(),
                        endStates);
        table.totalResult = table.totalResult.recordRound(table.outcome, score.seatScores());
        table.stage = QaRoundTable.Stage.ROUND_OVER;
        table.tingInfos().clear();
        events.add(eventFactory.winDeclared(revision, table));
        events.add(eventFactory.scoresSettled(revision, context, table));
        events.add(eventFactory.roundResultReady(revision, context, table));
        if (table.roundNumber >= table.maxPlayCount) {
            events.add(eventFactory.totalResultReady(revision, table));
        }
    }

    /** 流局（自建：墙尽即流局，无王牌保留），全员 EPS_DRAWN、零分。 */
    void declareDraw(
            QaRoundTable table, QaRoundContext context, long revision, List<GameEvent> events) {
        Map<Integer, Long> deltas = new LinkedHashMap<>();
        Map<Integer, String> endStates = new LinkedHashMap<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            deltas.put(seat, 0L);
            endStates.put(seat, "EPS_DRAWN");
        }
        table.outcome = new QaRoundTable.Outcome(-1, "DRAWN", null, deltas, endStates);
        Map<Integer, QaTaizhouScorer.SeatScore> seatScores = new LinkedHashMap<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            seatScores.put(seat, QaTaizhouScorer.zeroSeat());
        }
        table.totalResult = table.totalResult.recordRound(table.outcome, seatScores);
        table.stage = QaRoundTable.Stage.ROUND_OVER;
        table.tingInfos().clear();
        events.add(eventFactory.scoresSettled(revision, context, table));
        events.add(eventFactory.roundResultReady(revision, context, table));
        if (table.roundNumber >= table.maxPlayCount) {
            events.add(eventFactory.totalResultReady(revision, table));
        }
    }

    private int drawWithFlowerReplacement(
            QaRoundTable table, long revision, List<GameEvent> events, int seat) {
        if (table.wallExhausted()) {
            return -2; // 调用方按流局处理
        }
        int tile = drawCounted(table);
        while (QaTaizhouTiles.isWallFlower(tile)) {
            table.flowers().get(seat).add(tile);
            if (table.wallExhausted()) {
                return -2;
            }
            int replacement = drawCounted(table);
            events.add(eventFactory.flowerReplaced(revision, seat, tile, replacement));
            tile = replacement;
        }
        return tile;
    }

    /** 摸牌计数（自建）：每从墙摸走一张生牌数 -1，归零不再变化；发牌不经过此路径。 */
    private static int drawCounted(QaRoundTable table) {
        int tile = table.drawFromWall();
        if (table.shengPaiCount > 0) {
            table.shengPaiCount--;
        }
        return tile;
    }

    private List<List<Integer>> pongMelds(QaRoundTable table, int seat) {
        List<List<Integer>> pongMelds = new ArrayList<>();
        for (QaRoundTable.Meld meld : table.melds().get(seat)) {
            if (meld.combType().equals("PONG")) {
                pongMelds.add(meld.tiles());
            }
        }
        return pongMelds;
    }

    private static List<Integer> append(List<Integer> hand, int tile) {
        List<Integer> all = new ArrayList<>(hand);
        all.add(tile);
        return all;
    }
}
