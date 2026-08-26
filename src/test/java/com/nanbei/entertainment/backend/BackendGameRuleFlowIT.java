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
class BackendGameRuleFlowIT extends RoomFlowTestSupport {
    @Test
    void servesTheAuthenticatedTaizhouGoldRuleDocument() throws Exception {
        assertThat(get("/api/v1/game-rules/30400", null).statusCode()).isEqualTo(401);
        String accessToken = loginNewUser();

        HttpResponse<String> response = get("/api/v1/game-rules/30400", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode document = json(response.body());
        assertThat(document.path("gameId").asLong()).isEqualTo(30400L);
        assertThat(document.path("title").asText()).isEqualTo("台州麻将");
        assertThat(document.path("blocks")).isNotEmpty();
        assertThat(document.path("blocks").get(0).path("type").asText())
                .isEqualTo("HEADING");
        assertThat(document.path("blocks").get(0).path("text").asText())
                .isEqualTo("一、游戏规则");
        assertThat(document.path("blocks").size()).isEqualTo(56);
    }

    @Test
    void servesTheReviewedGoldRuleUsedByTheAndroidEntry() throws Exception {
        String accessToken = loginNewUser();

        HttpResponse<String> response = get("/api/v1/game-rules/30109", accessToken);
        HttpResponse<String> goldEntry = get("/api/v1/game-rules/30400", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(goldEntry.statusCode()).isEqualTo(200);
        JsonNode reviewedBlocks = json(response.body()).path("blocks");
        JsonNode goldBlocks = json(goldEntry.body()).path("blocks");
        String blocks = reviewedBlocks.toString();
        assertThat(reviewedBlocks.size()).isEqualTo(56);
        assertThat(goldBlocks).isEqualTo(reviewedBlocks);
        assertThat(blocks)
                .contains("牌点：")
                .contains("玩家A打出同一色使玩家B吃碰杠三口")
                .contains("能胡不胡")
                .contains("黄牌：")
                .contains("生牌阶段：")
                .contains("中发白生张")
                .contains("不死包：")
                .contains("七、算分方式")
                .contains("补杠：先碰后摸到第4张")
                .contains("封顶胡牌：")
                .doesNotContain("同祥", "模到", "事次", "刺下牌", "该坏家", "擦搭子");
    }
}
