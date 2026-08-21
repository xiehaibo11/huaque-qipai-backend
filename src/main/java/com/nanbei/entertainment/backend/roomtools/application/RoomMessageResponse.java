package com.nanbei.entertainment.backend.roomtools.application;

public record RoomMessageResponse(RoomMessageView message, boolean replayed) {
    RoomMessageResponse asReplay() {
        return new RoomMessageResponse(message, true);
    }
}
