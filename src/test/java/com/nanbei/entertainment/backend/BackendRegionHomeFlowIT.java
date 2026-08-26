package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentOutboxRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendRegionHomeFlowIT extends BackendFlowTestSupport {
    @Test
    void persistsOtpFailuresAndStopsFurtherAttempts() throws Exception {
        String phoneNumber = "13900139000";
        assertThat(
                        post(
                                        "/api/v1/auth/otp/request",
                                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(202);

        for (int attempt = 0; attempt < 5; attempt++) {
            HttpResponse<String> rejected =
                    post(
                            "/api/v1/auth/otp/verify",
                            "{\"phoneNumber\":\""
                                    + phoneNumber
                                    + "\",\"code\":\"000000\"}",
                            null,
                            null,
                            null);
            assertThat(rejected.statusCode()).isEqualTo(401);
        }

        HttpResponse<String> exhausted =
                post(
                        "/api/v1/auth/otp/verify",
                        "{\"phoneNumber\":\""
                                + phoneNumber
                                + "\",\"code\":\"246810\"}",
                        null,
                        null,
                        null);
        assertThat(exhausted.statusCode()).isEqualTo(429);
        assertThat(json(exhausted.body()).path("code").asText())
                .isEqualTo("AUTH_OTP_ATTEMPTS_EXCEEDED");
    }

    @Test
    void servesTheRecoveredRegionCatalogAndPersistsAUserSelection()
            throws Exception {
        HttpResponse<String> response = get("/api/v1/regions", null);
        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode catalog = json(response.body());
        assertThat(catalog.path("defaultLobbyId").asLong()).isEqualTo(900023L);
        assertThat(catalog.path("cities").size()).isEqualTo(11);
        int lobbyCount = 0;
        boolean foundTaizhou = false;
        for (JsonNode city : catalog.path("cities")) {
            lobbyCount += city.path("lobbies").size();
            if (city.path("code").asText().equals("taizhou")) {
                foundTaizhou = true;
                assertThat(city.path("mapX").asInt()).isEqualTo(949);
                assertThat(city.path("mapY").asInt()).isEqualTo(560);
                assertThat(city.path("lobbies").get(0).path("lobbyId").asLong())
                        .isEqualTo(900023L);
            }
        }
        assertThat(lobbyCount).isEqualTo(18);
        assertThat(foundTaizhou).isTrue();

        String phoneNumber = "13700137000";
        assertThat(
                        post(
                                        "/api/v1/auth/otp/request",
                                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(202);
        JsonNode tokens =
                json(
                        post(
                                        "/api/v1/auth/otp/verify",
                                        "{\"phoneNumber\":\""
                                                + phoneNumber
                                                + "\",\"code\":\"246810\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        String accessToken = tokens.path("accessToken").asText();
        HttpResponse<String> selected =
                put(
                        "/api/v1/regions/selection",
                        "{\"lobbyId\":900025}",
                        accessToken);
        assertThat(selected.statusCode()).isEqualTo(200);
        assertThat(json(selected.body()).path("lobbyId").asLong())
                .isEqualTo(900025L);
        assertThat(regionSelectionRepository.findAll())
                .anySatisfy(
                        selection ->
                                assertThat(selection.getLobbyId())
                                        .isEqualTo(900025L));
    }

    @Test
    void servesGameHomeForDebugLoginAndRefreshesTheSelectedRegion()
            throws Exception {
        HttpResponse<String> debugLogin =
                post("/api/v1/auth/debug", "{}", null, null, null);
        assertThat(debugLogin.statusCode()).isEqualTo(200);
        JsonNode debugTokens = json(debugLogin.body());
        String accessToken = debugTokens.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        HttpResponse<String> initialHome =
                get("/api/v1/home", accessToken);
        assertThat(initialHome.statusCode()).isEqualTo(200);
        JsonNode home = json(initialHome.body());
        assertThat(home.path("player").path("displayName").asText())
                .isEqualTo("开发账号");
        assertThat(home.path("player").path("publicPlayerId").asLong())
                .isPositive();
        assertThat(home.path("wallet").path("roomCards").asLong()).isZero();
        assertThat(home.path("wallet").path("coins").asLong()).isZero();
        assertThat(home.path("wallet").path("diamonds").asLong()).isZero();
        assertThat(home.path("region").path("lobbyId").asLong())
                .isEqualTo(900023L);
        assertThat(home.path("entries").isArray()).isTrue();
        assertThat(home.path("entries").size()).isGreaterThan(10);
        assertThat(home.path("announcements").isArray()).isTrue();
        assertThat(home.path("announcements").size()).isEqualTo(1);
        assertThat(home.path("announcements").get(0).path("content").asText())
                .isEqualTo("游戏公告:适当游戏益脑，沉迷游戏伤身");

        HttpResponse<String> selected =
                put(
                        "/api/v1/regions/selection",
                        "{\"lobbyId\":900025}",
                        accessToken);
        assertThat(selected.statusCode()).isEqualTo(200);

        JsonNode refreshedHome =
                json(get("/api/v1/home", accessToken).body());
        assertThat(refreshedHome.path("region").path("lobbyId").asLong())
                .isEqualTo(900025L);
        assertThat(refreshedHome.path("region").path("areaName").asText())
                .isEqualTo("杭州(宝宝)");
    }
}
