package com.nanbei.entertainment.backend.goldroom.application;

/** Idempotent answer for the gold-room leave endpoint; absence of a room also succeeds. */
public record GoldRoomLeaveResponse(String code) {
    public static GoldRoomLeaveResponse left() {
        return new GoldRoomLeaveResponse("GOLD_LEFT");
    }
}
