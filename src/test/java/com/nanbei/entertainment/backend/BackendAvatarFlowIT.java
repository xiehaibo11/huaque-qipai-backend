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
class BackendAvatarFlowIT extends BackendFlowTestSupport {
    @Test
    void migratesPlayerAvatarBytesAsPostgresBytea() {
        String dataType =
                jdbcTemplate.queryForObject(
                        """
                        SELECT data_type
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'player_avatars'
                          AND column_name = 'image_bytes'
                        """,
                        String.class);

        assertThat(dataType).isEqualTo("bytea");
    }

    @Test
    void uploadsReadsAndOverwritesTheAuthenticatedPlayersRealAvatar()
            throws Exception {
        String accessToken =
                json(post("/api/v1/auth/debug", "{}", null, null, null).body())
                        .path("accessToken")
                        .asText();
        assertThat(get("/api/v1/home", accessToken).statusCode()).isEqualTo(200);

        HttpResponse<String> upload =
                putMultipartAvatar(realJpeg(Color.ORANGE), accessToken);
        assertThat(upload.statusCode()).isEqualTo(200);
        JsonNode uploaded = json(upload.body());
        String avatarKey = uploaded.path("avatarKey").asText();
        String sha256 = uploaded.path("sha256").asText();
        assertThat(avatarKey).startsWith("avatar_");
        assertThat(sha256).matches("[0-9a-f]{64}");
        assertThat(uploaded.path("width").asInt()).isEqualTo(512);
        assertThat(uploaded.path("height").asInt()).isEqualTo(512);

        JsonNode home = json(get("/api/v1/home", accessToken).body());
        assertThat(home.path("player").path("avatarKey").asText())
                .isEqualTo(avatarKey);

        HttpResponse<byte[]> downloaded =
                getBytes("/api/v1/avatars/" + avatarKey, accessToken, null);
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.headers().firstValue("Content-Type"))
                .hasValue("image/jpeg");
        String etag = downloaded.headers().firstValue("ETag").orElseThrow();
        assertThat(etag).isEqualTo("\"" + sha256 + "\"");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(downloaded.body())))
                .isNotNull();

        HttpResponse<byte[]> notModified =
                getBytes("/api/v1/avatars/" + avatarKey, accessToken, etag);
        assertThat(notModified.statusCode()).isEqualTo(304);
        assertThat(notModified.body()).isEmpty();

        HttpResponse<String> overwrite =
                putMultipartAvatar(realJpeg(Color.CYAN), accessToken);
        assertThat(overwrite.statusCode()).isEqualTo(200);
        JsonNode overwritten = json(overwrite.body());
        assertThat(overwritten.path("avatarKey").asText()).isEqualTo(avatarKey);
        assertThat(overwritten.path("sha256").asText()).isNotEqualTo(sha256);
        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM player_avatars WHERE avatar_key = ?",
                        Integer.class,
                        avatarKey);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void rejectsUnauthenticatedAndInvalidAvatarUploads() throws Exception {
        assertThat(putMultipartAvatar(realJpeg(Color.MAGENTA), null).statusCode())
                .isEqualTo(401);

        String accessToken =
                json(post("/api/v1/auth/debug", "{}", null, null, null).body())
                        .path("accessToken")
                        .asText();
        HttpResponse<String> invalid =
                putMultipartAvatar(
                        "not-an-image".getBytes(StandardCharsets.UTF_8),
                        accessToken);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(json(invalid.body()).path("code").asText())
                .isEqualTo("AVATAR_INVALID_IMAGE");

        assertThat(
                        getBytes(
                                        "/api/v1/avatars/avatar_00000000-0000-0000-0000-000000000000",
                                        accessToken,
                                        null)
                                .statusCode())
                .isEqualTo(404);
    }
}
