package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 补杠的抢杠胡响应窗口与落地。 */
final class QaRobKongFlow {
    private final QaRoundEventFactory eventFactory;
    private final QaTaizhouBotPolicy botPolicy;

    QaRobKongFlow(QaRoundEventFactory eventFactory, QaTaizhouBotPolicy botPolicy) {
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
        this.botPolicy = Objects.requireNonNull(botPolicy, "botPolicy");
    }

    void open(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            int kongSeat,
            int tile,
            QaRoundTable.Meld pong) {
        table.pendingKong = new QaRoundTable.PendingKong(kongSeat, tile, pong);
        table.stage = QaRoundTable.Stage.AWAIT_CLAIMS;
        for (int seat = 1; seat <= table.chairCount; seat++) {
            if (seat == kongSeat || !canRobKong(table, seat, tile)) {
                continue;
            }
            QaRoundTable.PendingOffer offer =
                    new QaRoundTable.PendingOffer(
                            table.nextOfferId++,
                            UUID.randomUUID().toString(),
                            QaPowerMask.HU | QaPowerMask.CANCEL,
                            tile,
                            List.of(),
                            List.of(),
                            kongSeat,
                            false);
            offer.offeredAtEpochMilli = context.occurredAt().toEpochMilli();
            if (table.isBot(seat)) {
                if (botPolicy.decideRobKong(table, seat, tile).action()
                        == QaTaizhouBotPolicy.Action.PASS) {
                    continue;
                }
                offer.claimKind = QaClaim.Kind.HU;
            } else {
                events.add(eventFactory.actionOffered(revision, seat, offer));
            }
            table.offers().put(seat, offer);
        }
    }

    void adjudicate(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events) {
        QaRoundTable.PendingKong pending = table.pendingKong;
        QaClaim winner =
                QaAdjudicator.choose(table.pendingClaims(), pending.seat(), table.chairCount);
        for (Map.Entry<Integer, QaRoundTable.PendingOffer> entry : table.offers().entrySet()) {
            QaRoundTable.PendingOffer offer = entry.getValue();
            if (!offer.passed && (winner == null || entry.getKey() != winner.seat())) {
                events.add(eventFactory.actionExpired(revision, entry.getKey(), offer.offerId));
            }
        }
        table.offers().clear();
        table.pendingKong = null;
        if (winner != null) {
            turnDriver.declareWin(
                    table,
                    context,
                    revision,
                    events,
                    winner.seat(),
                    "QIANGGANG",
                    pending.seat(),
                    pending.tile());
            return;
        }
        finishFillKong(turnDriver, table, context, revision, events, pending);
    }

    private static void finishFillKong(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            QaRoundTable.PendingKong pending) {
        List<Integer> hand = table.hands().get(pending.seat());
        if (!table.melds().get(pending.seat()).remove(pending.pong())
                || !hand.remove(Integer.valueOf(pending.tile()))) {
            throw new IllegalStateException("pending fill kong state is invalid");
        }
        QaRoundTable.Meld meld =
                new QaRoundTable.Meld(
                        "FILL_KONG",
                        List.of(pending.tile(), pending.tile(), pending.tile(), pending.tile()),
                        pending.seat());
        table.melds().get(pending.seat()).add(meld);
        table.activeSeat = pending.seat();
        turnDriver.applyOwnKong(table, context, revision, events, pending.seat(), meld);
    }

    private static boolean canRobKong(QaRoundTable table, int seat, int tile) {
        List<Integer> hand = new ArrayList<>(table.hands().get(seat));
        hand.add(tile);
        return QaWinDetector.canWin(hand, table.jokerRule);
    }
}
