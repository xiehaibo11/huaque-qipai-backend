package com.nanbei.entertainment.backend.avatar.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public final class AvatarImageNormalizer {
    static final int MAX_UPLOAD_BYTES = 8 * 1024 * 1024;
    static final long MAX_PIXELS = 25_000_000L;
    static final int OUTPUT_SIZE = 512;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png");

    public NormalizedAvatar normalize(byte[] sourceBytes, String declaredContentType) {
        validateRequest(sourceBytes, declaredContentType);
        BufferedImage source = readValidatedImage(sourceBytes);
        BufferedImage output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        int cropSize = Math.min(source.getWidth(), source.getHeight());
        int cropX = (source.getWidth() - cropSize) / 2;
        int cropY = (source.getHeight() - cropSize) / 2;
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(
                    source,
                    0,
                    0,
                    OUTPUT_SIZE,
                    OUTPUT_SIZE,
                    cropX,
                    cropY,
                    cropX + cropSize,
                    cropY + cropSize,
                    null);
        } finally {
            graphics.dispose();
            source.flush();
        }
        byte[] encoded = encodeJpeg(output);
        output.flush();
        return new NormalizedAvatar(
                encoded,
                "image/jpeg",
                sha256(encoded),
                OUTPUT_SIZE,
                OUTPUT_SIZE);
    }

    private static void validateRequest(byte[] sourceBytes, String contentType) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw invalid("头像文件为空");
        }
        if (sourceBytes.length > MAX_UPLOAD_BYTES) {
            throw invalid("头像文件超过 8 MiB");
        }
        String normalizedType =
                contentType == null
                        ? ""
                        : contentType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            throw invalid("仅支持 JPEG 或 PNG 头像");
        }
    }

    private static BufferedImage readValidatedImage(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw invalid("无法读取头像图片");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalid("头像不是有效图片");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0
                        || height <= 0
                        || (long) width * (long) height > MAX_PIXELS) {
                    throw invalid("头像图片尺寸不符合要求");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalid("头像不是有效图片");
                }
                return decoded;
            } finally {
                reader.dispose();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid("头像不是有效图片");
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JVM does not provide a JPEG writer");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(0.90f);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode normalized avatar", exception);
        } finally {
            writer.dispose();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.AVATAR_INVALID_IMAGE, message);
    }
}
