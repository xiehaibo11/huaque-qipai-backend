package com.nanbei.entertainment.backend.region.application;

import java.time.Instant;
import java.util.UUID;

public record RegionSelection(UUID userId, long lobbyId, Instant updatedAt) {}
