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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

abstract class BackendFlowTestSupport {
    @LocalServerPort
    int port;

    @Autowired ObjectMapper objectMapper;
    @Autowired CryptoService cryptoService;
    @Autowired PaymentOutboxRepository outboxRepository;
    @Autowired UserRegionSelectionRepository regionSelectionRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
    }

    protected HttpResponse<String> get(String path, String bearer)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> put(
            String path, String body, String bearer) throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .PUT(
                                HttpRequest.BodyPublishers.ofString(
                                        body, StandardCharsets.UTF_8));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> putMultipartAvatar(
            byte[] jpeg, String bearer) throws Exception {
        String boundary = "NanbeiBoundary7MA4YWxkTrZu0gW";
        byte[] prefix =
                ("--"
                                + boundary
                                + "\r\n"
                                + "Content-Disposition: form-data; name=\"file\"; filename=\"avatar.jpg\"\r\n"
                                + "Content-Type: image/jpeg\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] suffix =
                ("\r\n--" + boundary + "--\r\n")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + jpeg.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(jpeg, 0, body, prefix.length, jpeg.length);
        System.arraycopy(suffix, 0, body, prefix.length + jpeg.length, suffix.length);
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri("/api/v1/profile/avatar"))
                        .header(
                                "Content-Type",
                                "multipart/form-data; boundary=" + boundary)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<byte[]> getBytes(
            String path, String bearer, String etag) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (etag != null) {
            builder.header("If-None-Match", etag);
        }
        return httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    protected static byte[] realJpeg(Color color) throws Exception {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.WHITE);
        graphics.fillOval(220, 80, 200, 320);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "jpeg", output)).isTrue();
        return output.toByteArray();
    }

    protected HttpResponse<String> delete(String path, String bearer)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri(path)).DELETE();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> post(
            String path,
            String body,
            String bearer,
            String headerName,
            String headerValue)
            throws Exception {        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body, StandardCharsets.UTF_8));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (headerName != null) {
            builder.header(headerName, headerValue);
        }
        return httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    protected JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
