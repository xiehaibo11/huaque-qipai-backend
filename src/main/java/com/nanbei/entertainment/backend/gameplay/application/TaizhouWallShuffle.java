package com.nanbei.entertainment.backend.gameplay.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 服务端权威牌墙：256 位种子、HMAC-SHA256 计数器 PRNG、无偏 Fisher-Yates。 */
final class TaizhouWallShuffle {
    static final String ALGORITHM = "HMAC_SHA256_FISHER_YATES_V1";
    private static final int SEED_BYTES = 32;
    private static final byte[] PRNG_DOMAIN =
            "TAIZHOU_WALL_PRNG_V1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMITMENT_DOMAIN =
            "TAIZHOU_WALL_COMMITMENT_V1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] QA_SEED_DOMAIN =
            "TAIZHOU_QA_SEED_V1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom SEED_SOURCE = new SecureRandom();

    private TaizhouWallShuffle() {}

    static Result secure() {
        byte[] seed = new byte[SEED_BYTES];
        SEED_SOURCE.nextBytes(seed);
        try {
            return fromSeed(seed, "JCA_SECURE_RANDOM_256/" + SEED_SOURCE.getAlgorithm());
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    static Result deterministic(long qaSeed) {
        MessageDigest digest = sha256();
        digest.update(QA_SEED_DOMAIN);
        byte[] seed = digest.digest(ByteBuffer.allocate(Long.BYTES).putLong(qaSeed).array());
        try {
            return fromSeed(seed, "QA_SHA256_DERIVED_256");
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    static Result fromSeed(byte[] seed) {
        return fromSeed(seed, "EXPLICIT_256_BIT_SEED");
    }

    private static Result fromSeed(byte[] seed, String seedSource) {
        if (seed == null || seed.length != SEED_BYTES) {
            throw new IllegalArgumentException("shuffle seed must contain 32 bytes");
        }
        List<Integer> wall = new ArrayList<>(QaTaizhouTiles.orderedWall());
        HmacSha256Prng prng = new HmacSha256Prng(seed);
        for (int index = wall.size() - 1; index > 0; index--) {
            Collections.swap(wall, index, prng.nextInt(index + 1));
        }
        return new Result(wall, ALGORITHM, seedSource, commitment(seed));
    }

    private static String commitment(byte[] seed) {
        MessageDigest digest = sha256();
        digest.update(COMMITMENT_DOMAIN);
        return HexFormat.of().formatHex(digest.digest(seed));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record Result(List<Integer> wall, String algorithm, String seedSource, String commitment) {
        Result {
            wall = List.copyOf(wall);
        }
    }

    /** 每个 32 字节块为 HMAC(seed, domain || counter)，有界取样使用拒绝采样消除模偏差。 */
    private static final class HmacSha256Prng {
        private static final long UINT32_RANGE = 1L << 32;
        private final Mac hmac;
        private long counter;
        private byte[] block = new byte[0];
        private int offset;

        private HmacSha256Prng(byte[] seed) {
            try {
                hmac = Mac.getInstance("HmacSHA256");
                hmac.init(new SecretKeySpec(seed, "HmacSHA256"));
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("HmacSHA256 is unavailable", exception);
            }
        }

        private int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            long limit = UINT32_RANGE - UINT32_RANGE % bound;
            long candidate;
            do {
                candidate = nextUnsignedInt();
            } while (candidate >= limit);
            return (int) (candidate % bound);
        }

        private long nextUnsignedInt() {
            if (offset + Integer.BYTES > block.length) {
                byte[] input =
                        ByteBuffer.allocate(PRNG_DOMAIN.length + Long.BYTES)
                                .put(PRNG_DOMAIN)
                                .putLong(counter++)
                                .array();
                block = hmac.doFinal(input);
                offset = 0;
            }
            long value =
                    ((block[offset] & 0xffL) << 24)
                            | ((block[offset + 1] & 0xffL) << 16)
                            | ((block[offset + 2] & 0xffL) << 8)
                            | (block[offset + 3] & 0xffL);
            offset += Integer.BYTES;
            return value;
        }
    }
}
