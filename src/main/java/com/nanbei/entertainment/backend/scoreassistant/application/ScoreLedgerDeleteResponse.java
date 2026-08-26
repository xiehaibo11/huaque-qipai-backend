package com.nanbei.entertainment.backend.scoreassistant.application;

import java.time.Instant;
import java.util.UUID;

public record ScoreLedgerDeleteResponse(UUID ledgerId, Instant deletedAt) {}
