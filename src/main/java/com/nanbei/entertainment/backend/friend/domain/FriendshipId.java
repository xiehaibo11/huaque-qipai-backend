package com.nanbei.entertainment.backend.friend.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class FriendshipId implements Serializable {
    private UUID userId;
    private UUID friendId;

    protected FriendshipId() {}

    public FriendshipId(UUID userId, UUID friendId) {
        this.userId = userId;
        this.friendId = friendId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFriendId() {
        return friendId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FriendshipId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(friendId, that.friendId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, friendId);
    }
}
