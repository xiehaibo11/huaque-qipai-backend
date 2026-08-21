package com.nanbei.entertainment.backend.fortune.application;

import java.time.Instant;

public record FortuneTreasureDrawItem(
        String treasureCode,
        String name,
        String quality,
        int fortuneScore,
        int level,
        Instant expiresAt) {}
