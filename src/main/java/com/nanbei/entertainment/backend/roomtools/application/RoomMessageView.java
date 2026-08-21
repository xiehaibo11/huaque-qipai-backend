package com.nanbei.entertainment.backend.roomtools.application;

import java.time.Instant;
import java.util.UUID;

public record RoomMessageView(
        UUID messageId,
        RoomMessageType type,
        int contentIndex,
        String text,
        UUID senderUserId,
        int senderSeat,
        int durationMillis,
        Instant createdAt) {}
