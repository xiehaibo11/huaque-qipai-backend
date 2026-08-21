package com.nanbei.entertainment.backend.gameplay.domain;

import java.util.List;
import java.util.Objects;

/** A successfully accepted command and its ordered events. */
public record CommandResult<S extends GameState>(S state, List<GameEvent> events) {
    public CommandResult {
        Objects.requireNonNull(state, "state");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    public static <S extends GameState> CommandResult<S> accepted(
            S previous, S next, List<GameEvent> events) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(next, "next");
        if (next.gameId() != previous.gameId()) {
            throw new IllegalArgumentException("gameId cannot change during a command");
        }
        if (next.revision() != previous.revision() + 1) {
            throw new IllegalArgumentException("accepted command must advance revision exactly once");
        }
        List<GameEvent> safeEvents = List.copyOf(Objects.requireNonNull(events, "events"));
        if (safeEvents.isEmpty()) {
            throw new IllegalArgumentException("accepted command must emit an event");
        }
        if (safeEvents.stream().anyMatch(event -> event.revision() != next.revision())) {
            throw new IllegalArgumentException("event revision must match the accepted state");
        }
        return new CommandResult<>(next, safeEvents);
    }
}
