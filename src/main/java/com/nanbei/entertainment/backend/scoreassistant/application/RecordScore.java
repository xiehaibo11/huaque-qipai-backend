package com.nanbei.entertainment.backend.scoreassistant.application;

import java.util.UUID;

public record RecordScore(UUID playerId, long scoreDelta) {}
