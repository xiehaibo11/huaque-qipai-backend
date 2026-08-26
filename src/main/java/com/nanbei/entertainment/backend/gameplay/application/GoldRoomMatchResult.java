package com.nanbei.entertainment.backend.gameplay.application;

/** A real-player gold match placement; ready means the server-authoritative round has started. */
public record GoldRoomMatchResult(String roomNumber, boolean ready) {}
