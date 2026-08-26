package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 弃牌后的吃碰杠胡并发响应窗口。 */
final class QaDiscardClaimFlow {
    private final QaRoundEventFactory eventFactory;
    private final QaTaizhouBotPolicy botPolicy;

    QaDiscardClaimFlow(QaRoundEventFactory eventFactory, QaTaizhouBotPolicy botPolicy) {
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
        this.botPolicy = Objects.requireNonNull(botPolicy, "botPolicy");
    }

    void open(
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events,
            int discarder,
            int tile) {
        int chowSeat = table.nextSeat(discarder);
        for (int seat = 1; seat <= table.chairCount; seat++) {
            if (seat == discarder) {
                continue;
            }
            List<Integer> hand = table.hands().get(seat);
            boolean canHu =
                    !table.passedHuTiles().get(seat).contains(tile)
                            && QaWinDetector.canWin(append(hand, tile), table.jokerRule);
            if (table.isBot(seat)) {
                QaTaizhouBotPolicy.Decision decision =
                        botPolicy.decideDiscardClaim(table, seat, discarder, tile);
                if (decision.action() != QaTaizhouBotPolicy.Action.PASS) {
                    addBotClaim(table, context, seat, discarder, tile, hand, decision);
                }
                continue;
            }
            int mask = canHu ? QaPowerMask.HU : QaPowerMask.NONE;
            List<QaMeldCandidates.KongOption> kongOptions = List.of();
            if (QaMeldCandidates.canExposedKong(hand, tile, table.jokerRule)) {
                mask |= QaPowerMask.MKONG;
                kongOptions = List.of(new QaMeldCandidates.KongOption("EXPOSED", tile));
            }
            if (!table.passedPungTiles().get(seat).contains(tile)
                    && QaMeldCandidates.canPung(hand, tile, table.jokerRule)) {
                mask |= QaPowerMask.PUNG;
            }
            List<List<Integer>> chowCandidates =
                    seat == chowSeat
                            ? QaMeldCandidates.chowCandidates(hand, tile, table.jokerRule)
                            : List.of();
            if (!chowCandidates.isEmpty()) {
                mask |= QaPowerMask.CHOW;
            }
            if (mask == QaPowerMask.NONE) {
                continue;
            }
            QaRoundTable.PendingOffer offer =
                    newOffer(
                            table,
                            context,
                            mask | QaPowerMask.CANCEL,
                            tile,
                            discarder,
                            chowCandidates,
                            kongOptions);
            table.offers().put(seat, offer);
            events.add(eventFactory.actionOffered(revision, seat, offer));
        }
    }

    private static void addBotClaim(
            QaRoundTable table,
            QaRoundContext context,
            int seat,
            int discarder,
            int tile,
            List<Integer> hand,
            QaTaizhouBotPolicy.Decision decision) {
        List<List<Integer>> chows =
                decision.action() == QaTaizhouBotPolicy.Action.CHOW
                        ? QaMeldCandidates.chowCandidates(hand, tile, table.jokerRule)
                        : List.of();
        List<QaMeldCandidates.KongOption> kongs =
                decision.action() == QaTaizhouBotPolicy.Action.KONG
                        ? List.of(new QaMeldCandidates.KongOption("EXPOSED", tile))
                        : List.of();
        int mask =
                switch (decision.action()) {
                    case HU -> QaPowerMask.HU;
                    case KONG -> QaPowerMask.MKONG;
                    case PUNG -> QaPowerMask.PUNG;
                    case CHOW -> QaPowerMask.CHOW;
                    default -> throw new IllegalStateException("invalid AI claim action");
                };
        QaRoundTable.PendingOffer offer =
                newOffer(table, context, mask, tile, discarder, chows, kongs);
        offer.claimKind =
                switch (decision.action()) {
                    case HU -> QaClaim.Kind.HU;
                    case KONG -> QaClaim.Kind.KONG;
                    case PUNG -> QaClaim.Kind.PUNG;
                    case CHOW -> QaClaim.Kind.CHOW;
                    default -> throw new IllegalStateException("invalid AI claim action");
                };
        if (decision.action() == QaTaizhouBotPolicy.Action.CHOW) {
            offer.candidateIndex = decision.candidateIndex();
        }
        table.offers().put(seat, offer);
    }

    private static QaRoundTable.PendingOffer newOffer(
            QaRoundTable table,
            QaRoundContext context,
            int mask,
            int tile,
            int discarder,
            List<List<Integer>> chowCandidates,
            List<QaMeldCandidates.KongOption> kongOptions) {
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
        return offer;
    }

    private static List<Integer> append(List<Integer> hand, int tile) {
        List<Integer> all = new ArrayList<>(hand);
        all.add(tile);
        return all;
    }
}
