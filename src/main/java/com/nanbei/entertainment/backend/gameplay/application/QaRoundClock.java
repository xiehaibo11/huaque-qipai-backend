package com.nanbei.entertainment.backend.gameplay.application;

import java.time.Instant;
import java.util.Map;

/** Server-projected QA turn timer value consumed by the Android TableClock layer. */
final class QaRoundClock {
    static final int MULTIPLE_CHOICE_SECONDS = 5;
    static final int TURN_SECONDS = 10;
    static final int CLAIM_SECONDS = 10;
    /** 服务端在展示归零后加宽限期才裁决，避免网络抖动把正常玩家误判成超时。 */
    static final int SWEEP_GRACE_SECONDS = 2;

    private QaRoundClock() {}

    static Integer remainingSeconds(QaRoundTable table) {
        return remainingSeconds(table, null);
    }

    /**
     * 倒计时按 offer 的真实发出时刻折算（对齐原版 msgClock 由服务端下发每阶段秒数）；
     * 旧状态没有时间戳或没有未作答的真人 offer 时退回静态窗口值。
     */
    static Integer remainingSeconds(QaRoundTable table, Instant now) {
        if (now == null || table.stage == QaRoundTable.Stage.ROUND_OVER) {
            return staticRemaining(table);
        }
        Integer soonest = null;
        for (Map.Entry<Integer, QaRoundTable.PendingOffer> entry : table.offers().entrySet()) {
            QaRoundTable.PendingOffer offer = entry.getValue();
            if (table.isBot(entry.getKey()) || offer.answered()) {
                continue;
            }
            Long stamp = offer.offeredAtEpochMilli;
            if (stamp == null) {
                continue;
            }
            int window =
                    offer.playOffer && offer.powerMask == QaPowerMask.PLAY
                            ? TURN_SECONDS
                            : CLAIM_SECONDS;
            long elapsedMillis = now.toEpochMilli() - stamp;
            int remaining = window - (int) Math.floorDiv(elapsedMillis, 1000L);
            if (soonest == null || remaining < soonest) {
                soonest = Math.max(0, remaining);
            }
        }
        return soonest != null ? soonest : staticRemaining(table);
    }

    private static Integer staticRemaining(QaRoundTable table) {
        return switch (table.stage) {
            case AWAIT_MULTIPLE -> MULTIPLE_CHOICE_SECONDS;
            case ROUND_OVER -> null;
            default -> TURN_SECONDS;
        };
    }

    static boolean isExpired(QaRoundTable.PendingOffer offer, Instant now) {
        Long stamp = offer.offeredAtEpochMilli;
        if (stamp == null) {
            return false;
        }
        int window =
                offer.playOffer && offer.powerMask == QaPowerMask.PLAY
                        ? TURN_SECONDS
                        : CLAIM_SECONDS;
        return now.toEpochMilli() - stamp >= (window + SWEEP_GRACE_SECONDS) * 1000L;
    }
}
