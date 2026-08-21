package com.nanbei.entertainment.backend.gameplay.domain;

public final class GameActionNotAllowedException extends RuntimeException {
    public GameActionNotAllowedException(String message) {
        super(message);
    }
}
