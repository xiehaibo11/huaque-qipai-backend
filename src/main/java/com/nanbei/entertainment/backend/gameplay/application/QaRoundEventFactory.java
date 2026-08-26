package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * QA 台州回合事件装配（南北自建测试事件流，非原版服务端协议）。
 * 公共/座位双发的 DEALT、DRAWN、DISCARDED 形状沿用旧脚本引擎；
 * ACTION_OFFERED、MELD_APPLIED、FLOWER_REPLACED、TURN_ADVANCED、ACTION_EXPIRED
 * 是本 Wave 新增的南北自建 QA 事件。
 */
final class QaRoundEventFactory {
    private final QaTaizhouProjection projection;
    private final TaizhouRoundMode mode;

    QaRoundEventFactory(QaTaizhouProjection projection) {
        this(projection, TaizhouRoundMode.QA);
    }

    QaRoundEventFactory(QaTaizhouProjection projection, TaizhouRoundMode mode) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    GameEvent botSeatsFilled(long revision, QaRoundContext context, int botPoolSize) {
        List<Map<String, Object>> seats = new ArrayList<>();
        for (QaMahjongAutoRoundEngine.SeatInput seat : context.seats()) {
            seats.add(
                    Map.of(
                            "seatNumber", seat.seatNumber(),
                            "userId", seat.userId().toString(),
                            "displayName", seat.displayName(),
                            "qaBot", seat.qaBot()));
        }
        Map<String, Object> payload = markerPayload();
        payload.put("botPoolSize", botPoolSize);
        payload.put("chairCount", context.chairCount());
        payload.put("seats", seats);
        return GameEvent.publicEvent(revision, "BOT_SEATS_FILLED", payload);
    }

    GameEvent wallShuffled(long revision, QaRoundTable table, int wallSize) {
        Map<String, Object> payload = markerPayload();
        payload.put("algorithm", table.shuffleAlgorithm);
        payload.put("seedSource", table.shuffleSeedSource);
        if (table.shuffleCommitment != null) {
            payload.put("commitment", table.shuffleCommitment);
        }
        payload.put("wallSize", wallSize);
        payload.put("remainingWallCount", table.wall.size());
        return GameEvent.publicEvent(revision, "WALL_SHUFFLED", payload);
    }

    GameEvent multipleChoiceStarted(long revision, QaRoundContext context, QaRoundTable table) {
        Map<String, Object> payload = basePayload(table, GamePhase.DEALING);
        payload.put("multipleChoice", projection.multipleChoice(context, table));
        return GameEvent.publicEvent(revision, "MULTIPLE_CHOICE_STARTED", payload);
    }

    GameEvent multipleChoiceChanged(long revision, QaRoundContext context, QaRoundTable table) {
        Map<String, Object> payload = basePayload(table, GamePhase.PLAYING);
        payload.put("multipleChoice", projection.multipleChoice(context, table));
        return GameEvent.publicEvent(revision, "MULTIPLE_CHOICE_CHANGED", payload);
    }

    GameEvent diceRolled(long revision, QaRoundTable table) {
        Map<String, Object> payload = basePayload(table, GamePhase.DEALING);
        Map<String, Object> diceRoll = QaTaizhouProjection.diceRollPayload(table.diceRoll);
        payload.put("diceRoll", diceRoll);
        return GameEvent.publicEvent(revision, "DICE_ROLLED", payload);
    }

    GameEvent wallOpened(long revision, QaRoundTable table) {
        Map<String, Object> payload = basePayload(table, GamePhase.DEALING);
        payload.put("wallState", wallStatePayload(table));
        payload.put(
                "openWall",
                Map.of(
                        "nIndex", table.wallOpenIndex,
                        "nMah", table.openTiles().get(0)));
        payload.put("jokerTiles", table.jokerRule.jokerTiles());
        payload.put("insteadTiles", table.jokerRule.insteadTiles());
        return GameEvent.publicEvent(revision, "WALL_OPENED", payload);
    }

    List<GameEvent> dealt(long revision, QaRoundContext context, QaRoundTable table) {
        Map<String, Object> publicPayload = basePayload(table, GamePhase.DEALING);
        publicPayload.put("publicRound", projection.publicRound(context, table));
        publicPayload.put("multipleChoice", projection.nullNode());
        publicPayload.put("diceRoll", projection.nullNode());
        List<GameEvent> events = new ArrayList<>();
        events.add(GameEvent.publicEvent(revision, "DEALT", publicPayload));
        for (int seat = 1; seat <= table.chairCount; seat++) {
            Map<String, Object> seatPayload = basePayload(table, GamePhase.DEALING);
            seatPayload.put("visibleRound", projection.visibleRound(context, table, seat));
            seatPayload.put("diceRoll", projection.nullNode());
            events.add(GameEvent.seatEvent(revision, "DEALT", seat, seatPayload));
        }
        return events;
    }

    List<GameEvent> drawn(long revision, QaRoundContext context, QaRoundTable table, int seat) {
        Map<String, Object> publicPayload = basePayload(table, GamePhase.PLAYING);
        publicPayload.put("publicRound", projection.publicRound(context, table));
        Map<String, Object> seatPayload = basePayload(table, GamePhase.PLAYING);
        seatPayload.put("visibleRound", projection.visibleRound(context, table, seat));
        return List.of(
                GameEvent.publicEvent(revision, "DRAWN", publicPayload),
                GameEvent.seatEvent(revision, "DRAWN", seat, seatPayload));
    }

    List<GameEvent> discarded(long revision, QaRoundContext context, QaRoundTable table, int seat) {
        Map<String, Object> lastDiscard = lastDiscardPayload(table);
        Map<String, Object> publicPayload = basePayload(table, GamePhase.PLAYING);
        publicPayload.put("publicRound", projection.publicRound(context, table));
        publicPayload.put("lastDiscard", lastDiscard);
        Map<String, Object> seatPayload = basePayload(table, GamePhase.PLAYING);
        seatPayload.put("visibleRound", projection.visibleRound(context, table, seat));
        seatPayload.put("lastDiscard", lastDiscard);
        return List.of(
                GameEvent.publicEvent(revision, "DISCARDED", publicPayload),
                GameEvent.seatEvent(revision, "DISCARDED", seat, seatPayload));
    }

    GameEvent actionOffered(long revision, int seat, QaRoundTable.PendingOffer offer) {
        return GameEvent.seatEvent(
                revision, "ACTION_OFFERED", seat, QaTaizhouProjection.actionOfferPayload(seat, offer));
    }

    GameEvent meldApplied(long revision, int seat, QaRoundTable.Meld meld) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seat", seat);
        payload.put("combType", meld.combType());
        payload.put("tiles", List.copyOf(meld.tiles()));
        payload.put("fromSeat", meld.fromSeat());
        return GameEvent.publicEvent(revision, "MELD_APPLIED", payload);
    }

    GameEvent flowerReplaced(long revision, int seat, int flower, int replacement) {
        return GameEvent.publicEvent(
                revision,
                "FLOWER_REPLACED",
                Map.of("seat", seat, "flower", flower, "replacement", replacement));
    }

    GameEvent turnAdvanced(long revision, QaRoundTable table) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activeSeat", table.activeSeat);
        payload.put("remainingWallCount", table.wall.size());
        if (table.isBot(table.activeSeat)) {
            long delay = QaTaizhouBotPolicy.thinkingDelayMillis(table);
            payload.put("clockRemainingSeconds", (int) ((delay + 999L) / 1_000L));
            payload.put("playbackDelayMillis", delay);
        } else {
            payload.put("clockRemainingSeconds", QaRoundClock.TURN_SECONDS);
        }
        return GameEvent.publicEvent(
                revision,
                "TURN_ADVANCED",
                payload);
    }

    GameEvent actionExpired(long revision, int seat, int offerId) {
        return GameEvent.seatEvent(
                revision, "ACTION_EXPIRED", seat, Map.of("seat", seat, "offerId", offerId));
    }

    /** TING_INFO（自建，对齐 msgTingMahInfo 语义）：出牌权开启时下发听牌映射，空数组表示无听。 */
    GameEvent tingInfo(long revision, int seat, List<QaRoundTable.TingEntry> entries) {
        return GameEvent.seatEvent(
                revision, "TING_INFO", seat, QaTaizhouProjection.tingInfoPayload(seat, entries));
    }

    /** SHENG_PAI_COUNT（自建，对齐 msgShengPaiCnt 语义）：首次在 DEALT 后下发，之后随摸牌递减。 */
    GameEvent shengPaiCount(long revision, int shengPaiCount, boolean first) {
        return GameEvent.publicEvent(
                revision,
                "SHENG_PAI_COUNT",
                Map.of("shengPaiCount", shengPaiCount, "bFirst", first));
    }

    /** LEFT_BANKER（自建，对齐 msgLeftBanker 语义）：每局开始各发一次。 */
    GameEvent leftBanker(long revision, int leftBankerCount) {
        return GameEvent.publicEvent(
                revision, "LEFT_BANKER", Map.of("leftBankerCount", leftBankerCount));
    }

    GameEvent winDeclared(long revision, QaRoundTable table) {
        Objects.requireNonNull(table.outcome, "outcome");
        QaRoundTable.Outcome outcome = table.outcome;
        Map<String, Object> payload = new LinkedHashMap<>();
        mode.putMarkers(payload);
        payload.put("phase", GamePhase.PLAYING.name());
        payload.put("roundNumber", table.roundNumber);
        payload.put("winnerSeat", outcome.winnerSeat());
        payload.put("winType", outcome.winType());
        payload.put("endPlayerState", "EPS_HU");
        if (outcome.discarderSeat() != null) {
            payload.put("discarderSeat", outcome.discarderSeat());
        }
        payload.put("trigger", mode.winTrigger());
        return GameEvent.publicEvent(revision, "WIN_DECLARED", payload);
    }

    GameEvent scoresSettled(long revision, QaRoundContext context, QaRoundTable table) {
        Objects.requireNonNull(table.outcome, "outcome");
        List<Map<String, Object>> scores = new ArrayList<>();
        for (QaMahjongAutoRoundEngine.SeatInput seat : context.seats()) {
            long delta = table.outcome.deltas().getOrDefault(seat.seatNumber(), 0L);
            scores.add(
                    Map.of(
                            "seatNumber", seat.seatNumber(),
                            "score", seat.score() + delta,
                            "delta", delta));
        }
        Map<String, Object> payload = markerPayload();
        payload.put("roundNumber", table.roundNumber);
        payload.put("scores", scores);
        return GameEvent.publicEvent(revision, "SCORES_SETTLED", payload);
    }

    GameEvent roundResultReady(long revision, QaRoundContext context, QaRoundTable table) {
        Map<String, Object> payload = markerPayload();
        payload.put("phase", GamePhase.ROUND_RESULT.name());
        payload.put("roundNumber", table.roundNumber);
        payload.put("settlement", projection.settlement(context, table));
        return GameEvent.publicEvent(revision, "ROUND_RESULT_READY", payload);
    }

    GameEvent totalResultReady(long revision, QaRoundTable table) {
        Map<String, Object> payload = markerPayload();
        payload.put("phase", GamePhase.ROUND_RESULT.name());
        payload.put("roundNumber", table.roundNumber);
        payload.put("totalResult", projection.totalResult(table));
        return GameEvent.publicEvent(revision, "TOTAL_RESULT_READY", payload);
    }

    private Map<String, Object> basePayload(QaRoundTable table, GamePhase phase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        mode.putMarkers(payload);
        payload.put("phase", phase.name());
        payload.put("roundNumber", table.roundNumber);
        payload.put("turnIndex", table.turnIndex);
        payload.put("activeSeat", table.activeSeat);
        payload.put("clockRemainingSeconds", QaRoundClock.remainingSeconds(table));
        payload.put("remainingWallCount", table.wall.size());
        if (table.wallFirstAsc >= 0) {
            payload.put("wallState", wallStatePayload(table));
        }
        return payload;
    }

    private static Map<String, Object> wallStatePayload(QaRoundTable table) {
        return Map.of(
                "nWallCnt", table.wall.size(),
                "nAsc", table.wallAsc,
                "nDesc", table.wallDesc,
                "nFirstAsc", table.wallFirstAsc,
                "nFirstDesc", table.wallFirstDesc,
                "bShow", 1,
                "nOpenIndex", table.wallOpenIndex);
    }

    private Map<String, Object> markerPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        mode.putMarkers(payload);
        return payload;
    }

    private static Map<String, Object> lastDiscardPayload(QaRoundTable table) {
        Objects.requireNonNull(table.lastDiscard, "lastDiscard");
        return Map.of(
                "seatNumber", table.lastDiscard.seat(),
                "tile", table.lastDiscard.tile(),
                "tileIndex", table.lastDiscard.tileIndex());
    }
}
