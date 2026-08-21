package com.nanbei.entertainment.backend.friend.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.friend.domain.FriendPresenceState;
import com.nanbei.entertainment.backend.friend.domain.FriendRelation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Locks REST response aliases back to original IMProtocol field names. */
class FriendOriginalProtocolCompatibilityTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void friendEntryAlsoSerializesOriginalListFieldNames()
            throws Exception {
        FriendEntry entry =
                new FriendEntry(
                        1084375590L,
                        "牌友甲",
                        "avatar_default",
                        FriendPresenceState.ONLINE,
                        Instant.parse("2026-07-31T08:00:00Z"),
                        true);

        JsonNode json = objectMapper.valueToTree(entry);

        assertThat(json.path("numid").asLong()).isEqualTo(1084375590L);
        assertThat(json.path("nickname").asText()).isEqualTo("牌友甲");
        assertThat(json.path("headurl").asText())
                .isEqualTo("avatar_default");
        assertThat(json.path("player_state").asInt()).isEqualTo(4);
        assertThat(json.path("shieldState").asInt()).isEqualTo(1);
        assertThat(json.path("last_login_time").asLong())
                .isEqualTo(Instant.parse("2026-07-31T08:00:00Z")
                        .getEpochSecond());
    }

    @Test
    void friendEntryAlsoSerializesOriginalWaitingRoomFields()
            throws Exception {
        FriendEntry entry =
                new FriendEntry(
                        1084375592L,
                        "牌友等开局",
                        "avatar_wait",
                        FriendPresenceState.WAITING,
                        Instant.parse("2026-07-31T08:00:00Z"),
                        false,
                        4,
                        3,
                        123456,
                        30588L,
                        true);

        JsonNode json = objectMapper.valueToTree(entry);

        assertThat(json.path("player_state").asInt()).isEqualTo(8);
        assertThat(json.path("chair_count").asInt()).isEqualTo(4);
        assertThat(json.path("user_count").asInt()).isEqualTo(3);
        assertThat(json.path("roomid").asInt()).isEqualTo(123456);
        assertThat(json.path("gameid").asLong()).isEqualTo(30588L);
        assertThat(json.path("bInTea").asBoolean()).isTrue();
        assertThat(json.path("areaid").asInt()).isZero();
        assertThat(json.path("channelid").asInt()).isZero();
        assertThat(json.path("last_fight_time").asLong()).isZero();
        assertThat(json.path("timeprop").isArray()).isTrue();
    }

    @Test
    void friendPageAlsoSerializesOriginalPaginationFields()
            throws Exception {
        FriendEntry entry =
                new FriendEntry(
                        1084375593L,
                        "牌友页",
                        null,
                        FriendPresenceState.ONLINE,
                        null,
                        false);
        FriendPage page = new FriendPage(1, 20, true, List.of(entry));

        JsonNode json = objectMapper.valueToTree(page);

        assertThat(json.path("cur_package").asInt()).isEqualTo(1);
        assertThat(json.path("total_package").asInt()).isEqualTo(3);
        assertThat(json.path("count").asInt()).isEqualTo(1);
        assertThat(json.path("friendInfo").get(0).path("numid").asLong())
                .isEqualTo(1084375593L);
    }

    @Test
    void searchResultAlsoSerializesOriginalAddFriendStateFields()
            throws Exception {
        FriendSearchResult result =
                new FriendSearchResult(
                        1084375590L,
                        "牌友乙",
                        null,
                        FriendRelation.REJECTED,
                        Instant.parse("2026-07-31T08:00:00Z"));

        JsonNode json = objectMapper.valueToTree(result);

        assertThat(json.path("numid").asLong()).isEqualTo(1084375590L);
        assertThat(json.path("nickname").asText()).isEqualTo("牌友乙");
        assertThat(json.path("state").asInt()).isEqualTo(2);
        assertThat(json.path("online").asBoolean()).isFalse();
        assertThat(json.path("lastLoginTime").asLong())
                .isEqualTo(Instant.parse("2026-07-31T08:00:00Z")
                        .getEpochSecond());
    }

    @Test
    void applicationViewAlsoSerializesOriginalApplyListFields()
            throws Exception {
        FriendApplicationView application =
                new FriendApplicationView(
                        UUID.randomUUID(),
                        1084375591L,
                        "申请人",
                        "avatar_apply",
                        Instant.parse("2026-07-31T08:00:00Z"));

        JsonNode json = objectMapper.valueToTree(application);

        assertThat(json.path("numid").asLong()).isEqualTo(1084375591L);
        assertThat(json.path("nickname").asText()).isEqualTo("申请人");
        assertThat(json.path("headurl").asText())
                .isEqualTo("avatar_apply");
        assertThat(json.path("online").asBoolean()).isFalse();
    }
}
