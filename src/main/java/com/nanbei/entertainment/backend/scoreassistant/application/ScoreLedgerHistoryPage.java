package com.nanbei.entertainment.backend.scoreassistant.application;

import java.util.List;

public record ScoreLedgerHistoryPage(
        int page,
        int pageSize,
        long totalCount,
        int totalPages,
        List<ScoreLedgerSummary> ledgers) {
    public ScoreLedgerHistoryPage {
        ledgers = List.copyOf(ledgers);
    }
}
