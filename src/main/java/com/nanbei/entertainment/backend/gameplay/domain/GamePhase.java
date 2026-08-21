package com.nanbei.entertainment.backend.gameplay.domain;

/** Lifecycle shared by every persistent game session. */
public enum GamePhase {
    WAITING,
    DEALING,
    PLAYING,
    ROUND_RESULT,
    COMPLETED,
    DISSOLVED
}
