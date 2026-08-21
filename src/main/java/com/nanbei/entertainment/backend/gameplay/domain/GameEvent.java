package com.nanbei.entertainment.backend.gameplay.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One ordered state transition with an explicit visibility boundary. */
public record GameEvent(
        long revision,
        String type,
        Audience audience,
        Integer targetSeat,
        Map<String, Object> payload) {
    public enum Audience {
        PUBLIC,
        SEAT
    }

    public GameEvent {
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        Objects.requireNonNull(audience, "audience");
        if (audience == Audience.PUBLIC && targetSeat != null) {
            throw new IllegalArgumentException("public event cannot target a seat");
        }
        if (audience == Audience.SEAT && (targetSeat == null || targetSeat <= 0)) {
            throw new IllegalArgumentException("seat event requires a positive target seat");
        }
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(payload, "payload")));
    }

    public static GameEvent publicEvent(
            long revision, String type, Map<String, Object> payload) {
        return new GameEvent(revision, type, Audience.PUBLIC, null, payload);
    }

    public static GameEvent seatEvent(
            long revision, String type, int targetSeat, Map<String, Object> payload) {
        return new GameEvent(revision, type, Audience.SEAT, targetSeat, payload);
    }
}
