package com.nanbei.entertainment.backend.fortune.application;

import java.time.Instant;

public record FortuneTreasureView(
        String treasureCode,
        String name,
        String quality,
        int fortuneScore,
        int level,
        Instant expiresAt,
        long remainingSeconds) {}
