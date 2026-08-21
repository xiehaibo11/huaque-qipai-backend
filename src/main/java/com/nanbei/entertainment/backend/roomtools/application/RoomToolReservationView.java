package com.nanbei.entertainment.backend.roomtools.application;

import java.time.Instant;

public record RoomToolReservationView(
        RoomToolType type,
        int targetRound,
        boolean active,
        Instant updatedAt) {}
