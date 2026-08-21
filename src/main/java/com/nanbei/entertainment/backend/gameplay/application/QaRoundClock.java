package com.nanbei.entertainment.backend.gameplay.application;

/** Server-projected QA turn timer value consumed by the Android TableClock layer. */
final class QaRoundClock {
    static final int DEFAULT_SECONDS = 20;

    private QaRoundClock() {}

    static Integer remainingSeconds(QaRoundTable table) {
        return table.stage == QaRoundTable.Stage.ROUND_OVER ? null : DEFAULT_SECONDS;
    }
}
