package com.nanbei.entertainment.backend.gameplay.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

public record GameplayCommandRequest(
        @NotNull GameplayCommandType type,
        @PositiveOrZero long expectedRevision,
        JsonNode payload) {
    public GameplayCommandRequest {
        Objects.requireNonNull(type, "type");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
    }

    public GameplayCommandRequest(GameplayCommandType type, long expectedRevision) {
        this(type, expectedRevision, null);
    }

    String canonicalValue(String roomNumber) {
        return roomNumber
                + "|"
                + type.name()
                + "|"
                + expectedRevision
                + "|"
                + (payload == null || payload.isNull() ? "" : payload.toString());
    }
}
