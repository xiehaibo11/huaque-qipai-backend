package com.nanbei.entertainment.backend.gameplay.domain;

/** Minimum state visible to the command-processing infrastructure. */
public interface GameState {
    long gameId();

    GamePhase phase();

    long revision();
}
