package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 原版服务端超时托管代打（自建）：出牌权宽限期后自动打出刚摸的牌（无摸牌标记时打
 * 第一张可打牌），弃牌/抢杠窗口里未作答的真人动作按过处理。
 */
final class QaRoundTimeoutFlow {
    private final QaRoundEventFactory eventFactory;

    QaRoundTimeoutFlow(QaRoundEventFactory eventFactory) {
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
    }

    /** 只读判定：是否存在已过宽限期的真人 offer（调度器的廉价预筛）。 */
    boolean hasTimedOutOffers(QaRoundTable table, Instant now) {
        if (table.outcome != null) {
            return false;
        }
        if (table.stage == QaRoundTable.Stage.AWAIT_PLAY && !table.isBot(table.activeSeat)) {
            QaRoundTable.PendingOffer offer = table.offers().get(table.activeSeat);
            if (offer != null && !offer.answered() && QaRoundClock.isExpired(offer, now)) {
                return true;
            }
        }
        if (table.stage == QaRoundTable.Stage.AWAIT_CLAIMS || table.pendingKong != null) {
            for (Map.Entry<Integer, QaRoundTable.PendingOffer> entry :
                    table.offers().entrySet()) {
                if (!table.isBot(entry.getKey())
                        && !entry.getValue().answered()
                        && QaRoundClock.isExpired(entry.getValue(), now)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 应用到期裁决，返回是否发生了推进；代打过牌后由引擎事件循环继续推进。 */
    boolean expireTimedOutOffers(
            QaRoundTurnDriver driver,
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events) {
        if (table.outcome != null) {
            return false;
        }
        Instant now = context.occurredAt();
        if (table.stage == QaRoundTable.Stage.AWAIT_PLAY && !table.isBot(table.activeSeat)) {
            QaRoundTable.PendingOffer offer = table.offers().get(table.activeSeat);
            if (offer != null && !offer.answered() && QaRoundClock.isExpired(offer, now)) {
                int seat = table.activeSeat;
                int tile = timeoutDiscardTile(table, seat);
                offer.passed = true;
                events.add(eventFactory.actionExpired(revision, seat, offer.offerId));
                driver.discard(table, context, revision, events, seat, tile);
                return true;
            }
        }
        boolean expiredAny = false;
        if (table.stage == QaRoundTable.Stage.AWAIT_CLAIMS || table.pendingKong != null) {
            for (Map.Entry<Integer, QaRoundTable.PendingOffer> entry :
                    table.offers().entrySet()) {
                int seat = entry.getKey();
                QaRoundTable.PendingOffer offer = entry.getValue();
                if (table.isBot(seat)
                        || offer.answered()
                        || !QaRoundClock.isExpired(offer, now)) {
                    continue;
                }
                offer.passed = true;
                events.add(eventFactory.actionExpired(revision, seat, offer.offerId));
                expiredAny = true;
            }
        }
        return expiredAny;
    }

    /** 超时代打选牌：刚摸的那张优先，否则手牌第一张可打牌。 */
    private static int timeoutDiscardTile(QaRoundTable table, int seat) {
        if (table.hasDrawnTile(seat)) {
            return table.drawnTile;
        }
        for (int tile : table.hands().get(seat)) {
            if (QaTaizhouTiles.isPlayable(tile)) {
                return tile;
            }
        }
        throw new IllegalStateException("timeout discard has no playable tile");
    }
}
