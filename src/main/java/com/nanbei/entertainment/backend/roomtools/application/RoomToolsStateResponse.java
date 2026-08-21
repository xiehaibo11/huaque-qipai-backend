package com.nanbei.entertainment.backend.roomtools.application;

import java.util.List;

public record RoomToolsStateResponse(
        String roomNumber,
        List<RoomToolDefinitionView> tools,
        List<RoomToolReservationView> reservations,
        List<String> quickPhrases,
        int emojiCount,
        List<RoomMessageView> messages) {}
