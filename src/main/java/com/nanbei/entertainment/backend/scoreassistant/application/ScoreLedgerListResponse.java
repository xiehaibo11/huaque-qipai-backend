package com.nanbei.entertainment.backend.scoreassistant.application;

import java.util.List;

public record ScoreLedgerListResponse(List<ScoreLedgerSummary> ledgers) {
    public ScoreLedgerListResponse {
        ledgers = List.copyOf(ledgers);
    }
}
