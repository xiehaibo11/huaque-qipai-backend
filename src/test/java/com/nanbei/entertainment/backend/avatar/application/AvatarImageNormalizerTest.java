package com.nanbei.entertainment.backend.avatar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.error.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AvatarImageNormalizerTest {
    private final AvatarImageNormalizer normalizer = new AvatarImageNormalizer();

    @Test
    void centerCropsAndReencodesPngAsSquareJpeg() throws Exception {
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 200, 400);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(200, 0, 400, 400);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(600, 0, 200, 400);
        graphics.dispose();

        NormalizedAvatar normalized = normalizer.normalize(encode(source, "png"), "image/png");

        assertThat(normalized.contentType()).isEqualTo("image/jpeg");
        assertThat(normalized.width()).isEqualTo(512);
        assertThat(normalized.height()).isEqualTo(512);
        assertThat(normalized.sha256()).matches("[0-9a-f]{64}");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(normalized.bytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
        Color left = new Color(decoded.getRGB(16, 256));
        Color right = new Color(decoded.getRGB(496, 256));
        assertThat(left.getGreen()).isGreaterThan(left.getRed());
        assertThat(right.getGreen()).isGreaterThan(right.getBlue());
    }

    @Test
    void rejectsEmptyAndUndecodablePayloads() {
        assertThatThrownBy(() -> normalizer.normalize(new byte[0], "image/jpeg"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                normalizer.normalize(
                                        "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                        "image/jpeg"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsPayloadOverEightMebibytesBeforeDecoding() {
        byte[] oversized = new byte[8 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> normalizer.normalize(oversized, "image/jpeg"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsUnsupportedDeclaredContentType() throws Exception {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(
                        () ->
                                normalizer.normalize(
                                        encode(source, "png"),
                                        "application/octet-stream"))
                .isInstanceOf(ApiException.class);
    }

    private static byte[] encode(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }
}
