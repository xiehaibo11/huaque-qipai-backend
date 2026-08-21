package com.nanbei.entertainment.backend.gameplay.domain;

/** Deterministically transforms one authoritative game state. */
@FunctionalInterface
public interface GameEngine<S extends GameState> {
    CommandResult<S> handle(S state, GameCommand command);
}
