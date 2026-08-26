package com.nanbei.entertainment.backend.gameplay.application;

import java.util.List;

/** 根据当前牌型和进度生成可复现的 8–15 秒出牌思考时长。 */
final class QaBotThinkingRhythm {
    static final long OPENING_BASE_MILLIS = 11_000L;
    static final long ROUTINE_BASE_MILLIS = 10_800L;
    static final int SETTLED_KINDS = 6;
    static final int SCATTERED_KINDS = 12;
    static final double SPREAD_GAIN = 0.20;
    static final double MELD_SPEEDUP = 0.02;
    static final double PACE_FLOOR = 0.92;
    static final double SKEW_FLOOR = 0.90;
    static final double SKEW_RANGE = 0.20;
    static final double SKEW_EXPONENT = 2.2;
    static final int HESITATION_PERCENT = 6;
    static final long HESITATION_BASE_MILLIS = 500L;
    static final long HESITATION_SPREAD_MILLIS = 800L;
    static final long MIN_MILLIS = 8_000L;
    static final long MAX_MILLIS = 15_000L;

    private QaBotThinkingRhythm() {}

    static long thinkingDelayMillis(QaRoundTable table) {
        int seat = table.activeSeat;
        List<Integer> hand = table.hands().get(seat);
        List<Integer> river = table.rivers().get(seat);
        int meldCount = table.melds().get(seat) == null ? 0 : table.melds().get(seat).size();
        boolean opening = river == null || river.isEmpty();

        long base = opening ? OPENING_BASE_MILLIS : ROUTINE_BASE_MILLIS;
        double complexity = complexityFactor(hand, meldCount);
        double pace = paceFactor(table.wall.size());
        double jitter = skewedJitter(noise(table, seat, 0x9E3779B9L));

        long delay = Math.round(base * complexity * pace * jitter);
        delay += hesitation(table, seat);
        return Math.max(MIN_MILLIS, Math.min(MAX_MILLIS, delay));
    }

    private static double complexityFactor(List<Integer> hand, int meldCount) {
        int kinds = distinctKinds(hand);
        double spread =
                clamp01(
                        (kinds - (double) SETTLED_KINDS)
                                / (SCATTERED_KINDS - (double) SETTLED_KINDS));
        double factor = 0.90 + SPREAD_GAIN * spread - MELD_SPEEDUP * meldCount;
        return Math.max(0.90, factor);
    }

    private static double paceFactor(int wallSize) {
        int live = wallSize - QaRoundTable.DRAW_RESERVE_TILES;
        int span = QaTaizhouTiles.WALL_SIZE - QaRoundTable.DRAW_RESERVE_TILES;
        double progress = clamp01(live / (double) span);
        return PACE_FLOOR + (1.0 - PACE_FLOOR) * progress;
    }

    private static double skewedJitter(double unit) {
        return SKEW_FLOOR + SKEW_RANGE * Math.pow(unit, SKEW_EXPONENT);
    }

    private static long hesitation(QaRoundTable table, int seat) {
        double roll = noise(table, seat, 0xC2B2AE3DL);
        if (roll * 100.0 >= HESITATION_PERCENT) {
            return 0L;
        }
        double magnitude = noise(table, seat, 0x27D4EB2FL);
        return HESITATION_BASE_MILLIS + Math.round(magnitude * HESITATION_SPREAD_MILLIS);
    }

    private static int distinctKinds(List<Integer> hand) {
        if (hand == null || hand.isEmpty()) {
            return SETTLED_KINDS;
        }
        boolean[] seen = new boolean[256];
        int kinds = 0;
        for (int tile : hand) {
            int index = tile & 0xFF;
            if (!seen[index]) {
                seen[index] = true;
                kinds++;
            }
        }
        return kinds;
    }

    private static double noise(QaRoundTable table, int seat, long salt) {
        long z = salt;
        z = mix(z + table.roundNumber * 0x1000193L);
        z = mix(z + (long) table.turnIndex * 0x01000193L);
        z = mix(z + (long) seat * 0x9E3779B1L);
        z = mix(z + table.wall.size());
        return (z >>> 11) / (double) (1L << 53);
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
