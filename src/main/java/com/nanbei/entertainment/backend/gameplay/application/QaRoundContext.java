package com.nanbei.entertainment.backend.gameplay.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 一次 QA 回合命令所需的展示与投影上下文（不含可变牌桌状态）。 */
record QaRoundContext(
        String roomNumber,
        String gameRuleDisplay,
        List<QaMahjongAutoRoundEngine.SeatInput> seats,
        Instant occurredAt) {
    QaRoundContext {
        if (roomNumber == null || !roomNumber.matches("\\d{6}")) {
            throw new IllegalArgumentException("invalid roomNumber");
        }
        if (gameRuleDisplay == null || gameRuleDisplay.isBlank()) {
            throw new IllegalArgumentException("gameRuleDisplay must not be blank");
        }
        seats = List.copyOf(Objects.requireNonNull(seats, "seats"));
        if (seats.size() != 2 && seats.size() != 4) {
            throw new IllegalArgumentException("QA round requires two or four seats");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    int chairCount() {
        return seats.size();
    }

    QaMahjongAutoRoundEngine.SeatInput seat(int seatNumber) {
        return seats.get(seatNumber - 1);
    }
}
