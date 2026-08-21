package com.nanbei.entertainment.backend.friend.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nanbei.entertainment.backend.friend.domain.FriendRelation;
import java.time.Instant;

public record FriendSearchResult(
        Long publicPlayerId,
        String displayName,
        String avatarKey,
        FriendRelation relation,
        Instant lastActiveAt) {
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

    /** Original RespAddFriendState.state: 0 none, 1 pending,
     * 2 rejected, 3 already friend. */
    @JsonProperty("state")
    public int originalState() {
        return switch (relation) {
            case PENDING -> 1;
            case REJECTED -> 2;
            case FRIEND -> 3;
            default -> 0;
        };
    }

    @JsonProperty("online")
    public boolean online() {
        return false;
    }

    @JsonProperty("lastLoginTime")
    public long lastLoginTime() {
        return lastActiveAt == null ? 0L : lastActiveAt.getEpochSecond();
    }
}
