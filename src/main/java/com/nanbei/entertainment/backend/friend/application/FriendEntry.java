package com.nanbei.entertainment.backend.friend.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nanbei.entertainment.backend.friend.domain.FriendPresenceState;
import java.time.Instant;
import java.util.List;

public record FriendEntry(
        Long publicPlayerId,
        String displayName,
        String avatarKey,
        FriendPresenceState state,
        Instant lastActiveAt,
        boolean shielded,
        int chairCount,
        int userCount,
        int roomId,
        long gameId,
        boolean inTea) {
    public FriendEntry(
            Long publicPlayerId,
            String displayName,
            String avatarKey,
            FriendPresenceState state,
            Instant lastActiveAt,
            boolean shielded) {
        this(
                publicPlayerId,
                displayName,
                avatarKey,
                state,
                lastActiveAt,
                shielded,
                0,
                0,
                0,
                0L,
                false);
    }

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

    @JsonProperty("player_state")
    public int playerState() {
        return state.playerState();
    }

    @JsonProperty("shieldState")
    public int shieldState() {
        return shielded ? 1 : 0;
    }

    @JsonProperty("last_login_time")
    public long lastLoginTime() {
        return lastActiveAt == null ? 0L : lastActiveAt.getEpochSecond();
    }

    @JsonProperty("areaid")
    public int areaId() {
        return 0;
    }

    @JsonProperty("channelid")
    public int channelId() {
        return 0;
    }

    @JsonProperty("last_fight_time")
    public long lastFightTime() {
        return 0L;
    }

    @JsonProperty("timeprop")
    public List<Integer> timeprop() {
        return List.of();
    }

    @JsonProperty("chair_count")
    public int chairCountOriginal() {
        return chairCount;
    }

    @JsonProperty("user_count")
    public int userCountOriginal() {
        return userCount;
    }

    @JsonProperty("roomid")
    public int roomIdOriginal() {
        return roomId;
    }

    @JsonProperty("gameid")
    public long gameIdOriginal() {
        return gameId;
    }

    @JsonProperty("bInTea")
    public boolean bInTea() {
        return inTea;
    }
}
