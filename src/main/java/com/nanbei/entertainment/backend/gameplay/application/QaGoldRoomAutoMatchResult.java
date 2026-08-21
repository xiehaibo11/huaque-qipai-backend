package com.nanbei.entertainment.backend.gameplay.application;

/** QA-only matched room produced by the local/test gold-room matcher. */
public record QaGoldRoomAutoMatchResult(String roomNumber, boolean autoGameplay) {}
