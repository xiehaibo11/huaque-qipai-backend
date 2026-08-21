package com.nanbei.entertainment.backend.gameplay.application;

import java.util.List;

/** 一座对当前弃牌的动作声明（吃/碰/杠/胡），由 QA 裁决器按优先级取舍。 */
record QaClaim(int seat, QaClaim.Kind kind) {
    enum Kind {
        CHOW,
        PUNG,
        KONG,
        HU
    }
}

/**
 * 南北自建 QA 裁决优先级，非原版服务端算法：胡 > 杠 > 碰 > 吃；
 * 同级取从出牌者起逆时针（座位号递增方向）最近的一家，一炮只响一家。
 */
final class QaAdjudicator {
    private QaAdjudicator() {}

    static QaClaim choose(List<QaClaim> claims, int discarderSeat, int chairCount) {
        QaClaim best = null;
        for (QaClaim claim : claims) {
            if (best == null || compare(claim, best, discarderSeat, chairCount) < 0) {
                best = claim;
            }
        }
        return best;
    }

    private static int compare(QaClaim left, QaClaim right, int discarderSeat, int chairCount) {
        int byPriority = Integer.compare(priority(right.kind()), priority(left.kind()));
        if (byPriority != 0) {
            return byPriority;
        }
        return Integer.compare(distance(left.seat(), discarderSeat, chairCount),
                distance(right.seat(), discarderSeat, chairCount));
    }

    private static int priority(QaClaim.Kind kind) {
        return switch (kind) {
            case HU -> 4;
            case KONG -> 3;
            case PUNG -> 2;
            case CHOW -> 1;
        };
    }

    private static int distance(int seat, int discarderSeat, int chairCount) {
        return Math.floorMod(seat - discarderSeat, chairCount);
    }
}
