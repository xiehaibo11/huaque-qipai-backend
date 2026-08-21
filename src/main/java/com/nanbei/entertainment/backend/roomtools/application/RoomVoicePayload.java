package com.nanbei.entertainment.backend.roomtools.application;

public record RoomVoicePayload(String mediaType, int durationMillis, byte[] data) {}
