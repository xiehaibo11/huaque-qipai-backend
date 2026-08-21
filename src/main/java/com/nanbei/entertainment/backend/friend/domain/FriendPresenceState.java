package com.nanbei.entertainment.backend.friend.domain;

public enum FriendPresenceState {
    OFFLINE(1),
    GAMING(2),
    ONLINE(4),
    WAITING(8);

    private final int playerState;

    FriendPresenceState(int playerState) {
        this.playerState = playerState;
    }

    public int playerState() {
        return playerState;
    }

    public static FriendPresenceState fromPlayerState(int playerState) {
        return switch (playerState) {
            case 2 -> GAMING;
            case 4 -> ONLINE;
            case 8 -> WAITING;
            default -> OFFLINE;
        };
    }
}
