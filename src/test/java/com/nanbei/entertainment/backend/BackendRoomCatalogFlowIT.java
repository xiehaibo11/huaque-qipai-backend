package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendRoomCatalogFlowIT extends RoomFlowTestSupport {
    @Test
    void servesOnlyTheEvidenceBackedLishuiGameListAndRuleConfig() throws Exception {
        assertThat(get("/api/v1/rooms/games?lobbyId=900038", null).statusCode())
                .isEqualTo(401);
        String accessToken = loginNewUser();

        HttpResponse<String> gamesResponse =
                get("/api/v1/rooms/games?lobbyId=900038", accessToken);
        assertThat(gamesResponse.statusCode()).isEqualTo(200);
        JsonNode games = json(gamesResponse.body());
        assertThat(games).hasSize(16);
        assertThat(games.get(0).path("gameId").asLong()).isEqualTo(30588L);
        assertThat(games.get(0).path("displayName").asText()).isEqualTo("乌龙玩法");
        assertThat(games.get(1).path("gameId").asLong()).isEqualTo(30089L);
        assertThat(games.get(2).path("displayName").asText()).isEqualTo("干瞪眼");
        assertThat(games)
                .noneMatch(game -> game.path("gameId").asLong() == 30306L);

        HttpResponse<String> configResponse =
                get(
                        "/api/v1/rooms/rule-config?lobbyId=900038&gameId=30588",
                        accessToken);
        assertThat(configResponse.statusCode()).isEqualTo(200);
        JsonNode config = json(configResponse.body());
        assertThat(config.path("version").asInt()).isEqualTo(1);
        assertThat(config.path("groups")).hasSize(11);
        assertThat(
                        config.path("groups")
                                .get(7)
                                .path("lines")
                                .get(0)
                                .path("options")
                                .get(0)
                                .path("costs")
                                .path("AA")
                                .path("0")
                                .asInt())
                .isEqualTo(25);

        JsonNode suichang =
                json(
                        get(
                                        "/api/v1/rooms/rule-config?lobbyId=900038&gameId=30300",
                                        accessToken)
                                .body());
        JsonNode categorySelector =
                suichang.path("categories")
                        .get(0)
                        .path("groups")
                        .get(0)
                        .path("lines")
                        .get(0)
                        .path("options")
                        .get(0);
        assertThat(categorySelector.path("categoryIndex").asInt()).isEqualTo(1);
        assertThat(categorySelector.path("condition").asText())
                .isEqualTo("ConditionRoomType");
        assertThat(categorySelector.path("conditionYes").asText()).isEqualTo("DaBan");
    }

    @Test
    void servesTaizhouOrderRuntimeMarksAndOriginalWulongCost() throws Exception {
        String accessToken = loginNewUser();

        JsonNode games =
                json(get("/api/v1/rooms/games?lobbyId=900023", accessToken).body());
        assertThat(games).hasSize(16);
        assertThat(games.get(0).path("gameId").asLong()).isEqualTo(30588L);
        assertThat(games.get(0).path("displayName").asText()).isEqualTo("茶苑双扣");
        assertThat(games.get(0).path("badge").asText()).isEqualTo("乌龙");
        assertThat(games.get(1).path("gameId").asLong()).isEqualTo(30577L);
        assertThat(games.get(2).path("gameId").asLong()).isEqualTo(30110L);
        assertThat(games.get(12).path("gameId").asLong()).isEqualTo(30130L);
        assertThat(games.get(12).path("badge").asText()).isEqualTo("台州");
        assertThat(games.get(15).path("gameId").asLong()).isEqualTo(30227L);
        assertThat(games.get(15).path("badge").asText()).isEqualTo("两帮");

        JsonNode config =
                json(
                        get(
                                        "/api/v1/rooms/rule-config?lobbyId=900023&gameId=30588",
                                        accessToken)
                                .body());
        JsonNode defaultEightRounds =
                config.path("groups")
                        .get(7)
                        .path("lines")
                        .get(0)
                        .path("options")
                        .get(1);
        assertThat(defaultEightRounds.path("node").asText()).isEqualTo("playCount_8");
        assertThat(defaultEightRounds.path("costs").path("ALL").path("0").asInt())
                .isEqualTo(400);
    }

    @Test
    void rejectsRuleConfigForADisabledGameEvenWhenItsConfigStillExists()
            throws Exception {
        String accessToken = loginNewUser();
        jdbcTemplate.update(
                "update room_games set enabled = false where lobby_id = ? and game_id = ?",
                900038L,
                30588L);
        try {
            HttpResponse<String> response =
                    get(
                            "/api/v1/rooms/rule-config?lobbyId=900038&gameId=30588",
                            accessToken);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(json(response.body()).path("code").asText())
                    .isEqualTo("ROOM_GAME_NOT_FOUND");
        } finally {
            jdbcTemplate.update(
                    "update room_games set enabled = true where lobby_id = ? and game_id = ?",
                    900038L,
                    30588L);
        }
    }
}
