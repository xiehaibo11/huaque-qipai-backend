package com.nanbei.entertainment.backend.gameplay.application;

/** Server-projected QA turn timer value consumed by the Android TableClock layer. */
final class QaRoundClock {
    static final int MULTIPLE_CHOICE_SECONDS = 5;
    static final int TURN_SECONDS = 10;

    private QaRoundClock() {}

    static Integer remainingSeconds(QaRoundTable table) {
        return switch (table.stage) {
            case AWAIT_MULTIPLE -> MULTIPLE_CHOICE_SECONDS;
            case ROUND_OVER -> null;
            default -> TURN_SECONDS;
        };
    }
}
