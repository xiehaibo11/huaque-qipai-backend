package com.nanbei.entertainment.backend.goldroom.application;

import jakarta.validation.constraints.Positive;

/** Client request for cancelling one pending original gold-room match (PlayerLeaveRequest). */
public record GoldRoomLeaveRequest(@Positive long lobbyId, @Positive int roomNameFlag) {}
