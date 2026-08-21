package com.nanbei.entertainment.backend.gameplay.domain;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

public record WaitingRoomState(
        long gameId,
        GamePhase phase,
        long revision,
        Map<Integer, UUID> occupants,
        Set<Integer> readySeats)
        implements GameState {
    public WaitingRoomState {
        if (gameId <= 0) {
            throw new IllegalArgumentException("gameId must be positive");
        }
        Objects.requireNonNull(phase, "phase");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        occupants = Map.copyOf(Objects.requireNonNull(occupants, "occupants"));
        readySeats = Set.copyOf(Objects.requireNonNull(readySeats, "readySeats"));
        if (occupants.keySet().stream().anyMatch(seat -> seat <= 0)) {
            throw new IllegalArgumentException("seat numbers must be positive");
        }
        if (!occupants.keySet().containsAll(readySeats)) {
            throw new IllegalArgumentException("ready seat must be occupied");
        }
        if (new HashSet<>(occupants.values()).size() != occupants.size()) {
            throw new IllegalArgumentException("a user cannot occupy multiple seats");
        }
    }

    public OptionalInt seatOf(UUID userId) {
        return occupants.entrySet().stream()
                .filter(entry -> entry.getValue().equals(userId))
                .mapToInt(Map.Entry::getKey)
                .findFirst();
    }

    public boolean isReady(int seatNumber) {
        return readySeats.contains(seatNumber);
    }

    public WaitingRoomState withReady(int seatNumber, boolean ready) {
        if (!occupants.containsKey(seatNumber)) {
            throw new IllegalArgumentException("seat is not occupied");
        }
        Set<Integer> nextReadySeats = new HashSet<>(readySeats);
        if (ready) {
            nextReadySeats.add(seatNumber);
        } else {
            nextReadySeats.remove(seatNumber);
        }
        return new WaitingRoomState(
                gameId, phase, revision + 1, occupants, nextReadySeats);
    }

    public WaitingRoomState withAllOccupiedReady() {
        return new WaitingRoomState(
                gameId, phase, revision + 1, occupants, occupants.keySet());
    }
}
