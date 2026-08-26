package com.nanbei.entertainment.backend.scoreassistant.application;

import java.time.YearMonth;

public record ScoreLedgerMonthlyStatistics(
        YearMonth month,
        int totalPlay,
        int winPlay,
        int lossPlay,
        long totalScore,
        long winScore,
        long lossScore,
        String winMax,
        String lostMax) {}
