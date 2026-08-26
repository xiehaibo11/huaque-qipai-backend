package com.nanbei.entertainment.backend.gameplay.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 一次 QA 回合命令所需的展示与投影上下文（不含可变牌桌状态）。 */
record QaRoundContext(
        String roomNumber,
        String gameRuleDisplay,
        List<QaMahjongAutoRoundEngine.SeatInput> seats,
        Instant occurredAt,
        boolean goldMode) {
    private static final Pattern BASE_SCORE = Pattern.compile("底分\\s*(\\d+)");
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

    QaRoundContext(
            String roomNumber,
            String gameRuleDisplay,
            List<QaMahjongAutoRoundEngine.SeatInput> seats,
            Instant occurredAt) {
        this(roomNumber, gameRuleDisplay, seats, occurredAt, false);
    }

    int chairCount() {
        return seats.size();
    }

    long baseScore() {
        Matcher matcher = BASE_SCORE.matcher(gameRuleDisplay);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 1L;
    }

    QaMahjongAutoRoundEngine.SeatInput seat(int seatNumber) {
        return seats.get(seatNumber - 1);
    }
}
