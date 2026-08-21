package com.nanbei.entertainment.backend.roomtools.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RoomMessageRequest(
        @NotNull RoomMessageType type,
        @Min(0) @Max(31) int contentIndex) {}
