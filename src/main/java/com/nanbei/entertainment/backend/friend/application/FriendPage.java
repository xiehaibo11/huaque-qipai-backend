package com.nanbei.entertainment.backend.friend.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FriendPage(
        int page, int size, boolean hasMore, List<FriendEntry> friends) {
    @JsonProperty("cur_package")
    public int curPackage() {
        return page;
    }

    @JsonProperty("total_package")
    public int totalPackage() {
        return hasMore ? page + 2 : page + 1;
    }

    @JsonProperty("count")
    public int count() {
        return friends.size();
    }

    @JsonProperty("friendInfo")
    public List<FriendEntry> friendInfo() {
        return friends;
    }
}
