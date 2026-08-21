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
class BackendGoldRoomCatalogFlowIT extends RoomFlowTestSupport {
    @Test
    void servesTheTaizhouGoldRoomCatalogAndRejectsAnonymousCallers() throws Exception {
        assertThat(get("/api/v1/gold-rooms/games?lobbyId=900023", null).statusCode())
                .isEqualTo(401);
        assertThat(get("/api/v1/gold-rooms/games/30400?lobbyId=900023", null).statusCode())
                .isEqualTo(401);
        String accessToken = loginNewUser();

        HttpResponse<String> gamesResponse =
                get("/api/v1/gold-rooms/games?lobbyId=900023", accessToken);
        assertThat(gamesResponse.statusCode()).isEqualTo(200);
        JsonNode games = json(gamesResponse.body());
        assertThat(games).hasSize(1);
        JsonNode game = games.get(0);
        // 金币场是 30400（GoldTaiZhouMahjong），复用房卡玩法 30109 的牌桌。
        assertThat(game.path("gameId").asLong()).isEqualTo(30400L);
        assertThat(game.path("displayName").asText()).isEqualTo("台州麻将");
        assertThat(game.path("boxGameId").asLong()).isEqualTo(30109L);
        assertThat(game.path("chairCount").asInt()).isEqualTo(4);
    }

    @Test
    void servesThreeOrderedLevelsMatchingTheOriginalScreenshot() throws Exception {
        String accessToken = loginNewUser();

        HttpResponse<String> response =
                get("/api/v1/gold-rooms/games/30400?lobbyId=900023", accessToken);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode conf = json(response.body());

        assertThat(conf.path("game").path("gameId").asLong()).isEqualTo(30400L);
        // roomFlags 决定选场页从左到右的卡片顺序。
        assertThat(conf.path("roomFlags")).hasSize(3);
        assertThat(conf.path("roomFlags").get(0).asInt()).isEqualTo(1);
        assertThat(conf.path("roomFlags").get(2).asInt()).isEqualTo(3);
        // 无真实金币场匹配之前不下发在线人数，保持原版 CSB 里 _panelPlayerCount 的隐藏默认值。
        assertThat(conf.path("showsPlayerCount").asBoolean()).isFalse();

        JsonNode levels = conf.path("levels");
        assertThat(levels).hasSize(3);

        JsonNode novice = levels.get(0);
        assertThat(novice.path("uiType").asInt()).isEqualTo(1);
        assertThat(novice.path("baseScore").asLong()).isEqualTo(200L);
        assertThat(novice.path("dynamicCost").asBoolean()).isTrue();
        assertThat(novice.path("minRich").asLong()).isEqualTo(1000L);
        assertThat(novice.path("maxRich").asLong()).isEqualTo(60000L);
        // 新手场没有绶带；后端配置了 non_null 序列化，未配置的标签整个字段不下发。
        assertThat(novice.has("tagRibbon1")).isFalse();
        assertThat(novice.has("tagRibbon2")).isFalse();

        JsonNode advanced = levels.get(1);
        assertThat(advanced.path("uiType").asInt()).isEqualTo(2);
        assertThat(advanced.path("baseScore").asLong()).isEqualTo(600L);
        assertThat(advanced.path("minRich").asLong()).isEqualTo(30000L);
        assertThat(advanced.path("maxRich").asLong()).isEqualTo(200000L);
        assertThat(advanced.path("tagRibbon1").asText()).isEqualTo("2#底分进阶，挑战高分");

        JsonNode senior = levels.get(2);
        assertThat(senior.path("uiType").asInt()).isEqualTo(3);
        assertThat(senior.path("baseScore").asLong()).isEqualTo(1000L);
        assertThat(senior.path("minRich").asLong()).isEqualTo(50000L);
        // -1 表示无上限，客户端渲染为「5万以上」。
        assertThat(senior.path("maxRich").asLong()).isEqualTo(-1L);
        assertThat(senior.path("tagRibbon1").asText())
                .isEqualTo("3#EEEE55_支持加倍！强者之战");
    }

    @Test
    void rejectsUnknownGoldGames() throws Exception {
        String accessToken = loginNewUser();

        HttpResponse<String> response =
                get("/api/v1/gold-rooms/games/30109?lobbyId=900023", accessToken);
        // 30109 是房卡玩法，不在金币场目录里。
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response.body()).path("code").asText()).isEqualTo("GOLD_GAME_NOT_FOUND");
    }
}
