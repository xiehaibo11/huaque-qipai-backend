package com.nanbei.entertainment.backend.friend.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FriendApplicationView(
        UUID id,
        Long publicPlayerId,
        String displayName,
        String avatarKey,
        Instant createdAt) {
    @JsonProperty("numid")
    public Long numid() {
        return publicPlayerId;
    }

    @JsonProperty("nickname")
    public String nickname() {
        return displayName;
    }

    @JsonProperty("headurl")
    public String headurl() {
        return avatarKey;
    }

    @JsonProperty("online")
    public boolean online() {
        return false;
    }

    public record FriendApplicationList(
            long total, List<FriendApplicationView> applications) {}
}
