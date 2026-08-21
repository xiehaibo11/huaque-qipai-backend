package com.nanbei.entertainment.backend.goldroom.application;

import jakarta.validation.constraints.Positive;

/** Client request for joining one original gold-room level. */
public record GoldRoomJoinRequest(@Positive long lobbyId, @Positive int roomNameFlag) {
    public String canonicalValue(long gameId) {
        return "lobbyId="
                + lobbyId
                + ";gameId="
                + gameId
                + ";roomNameFlag="
                + roomNameFlag;
    }
}
