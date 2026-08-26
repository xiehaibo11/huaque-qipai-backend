package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

abstract class RoomFlowTestSupport extends BackendFlowTestSupport {
    protected String loginNewUser() throws Exception {
        return loginUser().accessToken();
    }

    protected Login loginUser() throws Exception {
        String phone = "139" + String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
        HttpResponse<String> requested =
                post(
                        "/api/v1/auth/otp/request",
                        "{\"phoneNumber\":\"" + phone + "\"}",
                        null,
                        null,
                        null);
        assertThat(requested.statusCode()).isEqualTo(202);
        JsonNode tokens =
                json(
                        post(
                                        "/api/v1/auth/otp/verify",
                                        "{\"phoneNumber\":\""
                                                + phone
                                                + "\",\"code\":\"246810\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        String accessToken = tokens.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        UUID userId =
                jdbcTemplate.queryForObject(
                        """
                        select user_id from user_identities
                        where provider = 'PHONE' and provider_subject = ?
                        """,
                        UUID.class,
                        phone);
        jdbcTemplate.update(
                """
                insert into player_wallets (
                    user_id, room_card_centi, coins, diamonds,
                    bound_room_cards, coupons, updated_at, version
                ) values (?, 0, 0, 0, 0, 0, current_timestamp, 0)
                on conflict (user_id) do nothing
                """,
                userId);
        lastUserId = userId;
        return new Login(userId, accessToken);
    }

    protected UUID lastUserId;

    protected void setRoomCardCenti(UUID userId, long amount) {
        jdbcTemplate.update(
                "update player_wallets set room_card_centi = ?, updated_at = current_timestamp where user_id = ?",
                amount,
                userId);
    }

    protected long roomCardCenti(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select room_card_centi from player_wallets where user_id = ?",
                Long.class,
                userId);
    }

    protected void setCoins(UUID userId, long amount) {
        jdbcTemplate.update(
                "update player_wallets set coins = ?, updated_at = current_timestamp where user_id = ?",
                amount,
                userId);
    }

    /**
     * QA 完整轮转驱动：按当前快照里的 playPermission/actionOffer 依次作答，直到 ROUND_RESULT。
     * 只用于 QA 会话（state 带 qaDisclosure），引擎规则为南北自建。
     */
    protected void driveQaTaizhouRoundToCompletion(String accessToken, String roomNumber)
            throws Exception {
        for (int iteration = 0; iteration < 400; iteration++) {
            JsonNode snapshot =
                    json(get("/api/v1/game-sessions/" + roomNumber, accessToken).body());
            if ("ROUND_RESULT".equals(snapshot.path("phase").asText())) {
                return;
            }
            long revision = snapshot.path("revision").asLong();
            if (snapshot.path("multipleChoice").path("choiceActive").asBoolean(false)) {
                HttpResponse<String> choice =
                        post(
                                "/api/v1/game-sessions/" + roomNumber + "/commands",
                                "{\"type\":\"MULTIPLE_CHOICE\",\"expectedRevision\":"
                                        + revision
                                        + ",\"payload\":{\"choice\":\"NONE\"}}",
                                accessToken,
                                "Idempotency-Key",
                                "qa-choice-" + UUID.randomUUID());
                assertThat(choice.statusCode()).isEqualTo(200);
                continue;
            }
            String body = qaRoundCommandBody(snapshot, revision);
            assertThat(body)
                    .as("QA round %s stopped without an actionable snapshot command", roomNumber)
                    .isNotBlank();
            HttpResponse<String> command =
                    post(
                            "/api/v1/game-sessions/" + roomNumber + "/commands",
                            body,
                            accessToken,
                            "Idempotency-Key",
                            "qa-round-" + UUID.randomUUID());
            assertThat(command.statusCode()).isEqualTo(200);
        }
        throw new AssertionError("QA round did not reach ROUND_RESULT within 400 commands");
    }

    private static String qaRoundCommandBody(JsonNode snapshot, long revision) {
        JsonNode offer = snapshot.path("actionOffer");
        if (!offer.path("actionToken").asText().isBlank()) {
            return qaRoundActionOfferCommandBody(offer, snapshot, revision);
        }
        JsonNode playPermission = snapshot.path("playPermission");
        String token = playPermission.path("actionToken").asText();
        if (token.isBlank()) {
            return "";
        }
        return commandJson(
                "DISCARD",
                revision,
                "{\"tileValue\":" + maxConcealedTile(snapshot)
                        + ",\"actionToken\":\"" + token + "\"}");
    }

    private static String qaRoundActionOfferCommandBody(
            JsonNode offer, JsonNode snapshot, long revision) {
        int mask = offer.path("powerMask").asInt();
        String token = offer.path("actionToken").asText();
        if ((mask & 0x010) != 0) {
            return commandJson("HU", revision, "{\"actionToken\":\"" + token + "\"}");
        }
        if ((mask & 0x002) != 0) {
            int mySeat = snapshot.path("mySeat").asInt();
            int tile = -1;
            for (JsonNode hand : snapshot.path("visibleRound").path("hands")) {
                if (hand.path("seatNumber").asInt() == mySeat) {
                    for (JsonNode concealed : hand.path("concealedTiles")) {
                        tile = Math.max(tile, concealed.asInt());
                    }
                }
            }
            assertThat(tile).isPositive();
            return commandJson(
                    "DISCARD",
                    revision,
                    "{\"tileValue\":" + tile + ",\"actionToken\":\"" + token + "\"}");
        }
        JsonNode kongOptions = offer.path("kongOptions");
        if ((mask & (0x020 | 0x040 | 0x080)) != 0 && kongOptions.size() > 0) {
            JsonNode option = kongOptions.get(0);
            return commandJson(
                    "KONG",
                    revision,
                    "{\"tileValue\":" + option.path("tileValue").asInt()
                            + ",\"kongType\":\"" + option.path("kongType").asText()
                            + "\",\"actionToken\":\"" + token + "\"}");
        }
        if ((mask & 0x008) != 0) {
            return commandJson(
                    "PUNG",
                    revision,
                    "{\"tileValue\":" + offer.path("contextTile").asInt()
                            + ",\"actionToken\":\"" + token + "\"}");
        }
        if ((mask & 0x004) != 0) {
            return commandJson(
                    "CHOW",
                    revision,
                    "{\"tileValue\":" + offer.path("contextTile").asInt()
                            + ",\"candidateIndex\":0,\"actionToken\":\"" + token + "\"}");
        }
        return commandJson("PASS", revision, "{\"actionToken\":\"" + token + "\"}");
    }

    private static int maxConcealedTile(JsonNode snapshot) {
        int mySeat = snapshot.path("mySeat").asInt();
        int tile = -1;
        for (JsonNode hand : snapshot.path("visibleRound").path("hands")) {
            if (hand.path("seatNumber").asInt() == mySeat) {
                for (JsonNode concealed : hand.path("concealedTiles")) {
                    tile = Math.max(tile, concealed.asInt());
                }
            }
        }
        assertThat(tile).isPositive();
        return tile;
    }

    private static String commandJson(String type, long expectedRevision, String payload) {
        return "{\"type\":\""
                + type
                + "\",\"expectedRevision\":"
                + expectedRevision
                + ",\"payload\":"
                + payload
                + "}";
    }

    protected long gameSessionCount() {
        return jdbcTemplate.queryForObject("select count(*) from game_sessions", Long.class);
    }

    protected static String defaultWulongRequest() {
        return """
                {
                  "lobbyId": 900038,
                  "gameId": 30588,
                  "categoryIndex": 1,
                  "selectedNodeNames": [
                    "FiveHalfDeck='1';CanContinue='1';",
                    "ShowCardCount",
                    "RankScore='0';",
                    "LastPlayerScoreToFirst='1';",
                    "gamezhang='6';",
                    "BombRewardMultiplier='30';",
                    "playerCount_4",
                    "playCount_4",
                    "PayType='0';",
                    "AutoReady",
                    "IsSysTrust='15';"
                  ]
                }
                """;
    }

    protected static String defaultWulongAaRequest() {
        return defaultWulongRequest().replace("PayType='0';", "PayType='1';");
    }

    protected record Login(UUID userId, String accessToken) {}
}
