package com.nanbei.entertainment.backend.fortune.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.fortune.domain.FortuneOperationEntity;
import com.nanbei.entertainment.backend.fortune.domain.FortuneStateEntity;
import com.nanbei.entertainment.backend.fortune.domain.FortuneTreasureEntity;
import com.nanbei.entertainment.backend.fortune.infrastructure.FortuneOperationRepository;
import com.nanbei.entertainment.backend.fortune.infrastructure.FortuneStateRepository;
import com.nanbei.entertainment.backend.fortune.infrastructure.FortuneTreasureRepository;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class FortuneService {
    private final FortuneStateRepository stateRepository;
    private final FortuneTreasureRepository treasureRepository;
    private final FortuneOperationRepository operationRepository;
    private final PlayerWalletRepository walletRepository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final IntUnaryOperator randomIndex;

    @Autowired
    public FortuneService(
            FortuneStateRepository stateRepository,
            FortuneTreasureRepository treasureRepository,
            FortuneOperationRepository operationRepository,
            PlayerWalletRepository walletRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper) {
        this(
                stateRepository,
                treasureRepository,
                operationRepository,
                walletRepository,
                cryptoService,
                objectMapper,
                Clock.systemUTC(),
                new SecureRandom()::nextInt);
    }

    FortuneService(
            FortuneStateRepository stateRepository,
            FortuneTreasureRepository treasureRepository,
            FortuneOperationRepository operationRepository,
            PlayerWalletRepository walletRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            Clock clock,
            IntUnaryOperator randomIndex) {
        this.stateRepository = stateRepository;
        this.treasureRepository = treasureRepository;
        this.operationRepository = operationRepository;
        this.walletRepository = walletRepository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.randomIndex = randomIndex;
    }

    @Transactional(readOnly = true)
    public FortuneStateResponse state(UUID userId) {
        Instant now = clock.instant();
        FortuneStateEntity state =
                stateRepository.findById(userId).orElseGet(() -> new FortuneStateEntity(userId, now));
        ShopWalletResponse wallet =
                walletRepository
                        .findById(userId)
                        .map(ShopWalletResponse::from)
                        .orElseGet(() -> new ShopWalletResponse(0, 0, 0, 0));
        List<FortuneTreasureView> treasures =
                treasureRepository.findByUserIdOrderByTreasureCode(userId).stream()
                        .map(entity -> treasureView(entity, now))
                        .toList();
        return new FortuneStateResponse(
                wallet,
                state.getWealthPoints(),
                state.getLuckPoints(),
                FortuneCatalog.PRAYERS,
                FortuneCatalog.TREASURES,
                FortuneCatalog.CAISHEN,
                treasures,
                state.getCaishenExpiresAt(),
                remaining(state.getCaishenExpiresAt(), now),
                FortuneCatalog.TREASURE_ONE_DRAW_PRICE_DIAMONDS,
                FortuneCatalog.TREASURE_FIVE_DRAW_PRICE_DIAMONDS,
                FortuneCatalog.TREASURE_FIVE_DRAW_DISCOUNT_TENTHS);
    }

    @Transactional
    public FortunePrayerResponse pray(
            UUID userId, String idempotencyKey, String productCode, int quantity) {
        if (quantity < 1 || quantity > 10) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "求财运数量必须为 1 到 10");
        }
        FortunePrayerProduct product = prayerProduct(productCode);
        String key = requireKey(idempotencyKey);
        String hash = hash("PRAYER|" + productCode + "|" + quantity);
        FortunePrayerResponse replay = replay(userId, key, hash, FortunePrayerResponse.class);
        if (replay != null) {
            return replay.asReplay();
        }
        lockAccount(userId);
        FortuneStateEntity state = lockedState(userId);
        PlayerWalletEntity wallet = lockedWallet(userId);
        long cost = discountedCost(product.priceDiamonds(), quantity);
        debit(wallet, cost);
        int wealth = Math.multiplyExact(product.wealthPoints(), quantity);
        int luck = Math.multiplyExact(product.luckPoints(), quantity);
        state.addProgress(wealth, luck, clock.instant());
        walletRepository.save(wallet);
        stateRepository.save(state);
        FortunePrayerResponse response =
                new FortunePrayerResponse(
                        productCode,
                        quantity,
                        cost,
                        state.getWealthPoints(),
                        state.getLuckPoints(),
                        ShopWalletResponse.from(wallet),
                        false);
        saveOperation(userId, key, hash, "PRAYER", response);
        return response;
    }

    @Transactional
    public FortuneTreasureDrawResponse drawTreasures(
            UUID userId, String idempotencyKey, int count) {
        if (count != 1 && count != 5) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "聚宝盆只支持抽取 1 次或 5 次");
        }
        String key = requireKey(idempotencyKey);
        String hash = hash("TREASURE_DRAW|" + count);
        FortuneTreasureDrawResponse replay =
                replay(userId, key, hash, FortuneTreasureDrawResponse.class);
        if (replay != null) {
            return replay.asReplay();
        }
        lockAccount(userId);
        lockedState(userId);
        PlayerWalletEntity wallet = lockedWallet(userId);
        long cost =
                count == 1
                        ? FortuneCatalog.TREASURE_ONE_DRAW_PRICE_DIAMONDS
                        : FortuneCatalog.TREASURE_FIVE_DRAW_PRICE_DIAMONDS;
        debit(wallet, cost);
        List<FortuneTreasureDrawItem> draws = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            FortuneTreasureProduct product = randomTreasure();
            var existing =
                    treasureRepository.findByUserIdAndTreasureCode(
                            userId, product.treasureCode());
            FortuneTreasureEntity treasure =
                    existing.orElseGet(
                                    () ->
                                            new FortuneTreasureEntity(
                                                    userId,
                                                    product.treasureCode(),
                                                    clock.instant()));
            if (existing.isPresent()) {
                treasure.refresh(clock.instant());
            }
            treasureRepository.save(treasure);
            draws.add(drawItem(product, treasure));
        }
        walletRepository.save(wallet);
        FortuneTreasureDrawResponse response =
                new FortuneTreasureDrawResponse(
                        count, cost, List.copyOf(draws), ShopWalletResponse.from(wallet), false);
        saveOperation(userId, key, hash, "TREASURE_DRAW", response);
        return response;
    }

    @Transactional
    public FortuneCaishenResponse activateCaishen(
            UUID userId, String idempotencyKey, String productCode) {
        FortuneCaishenProduct product = caishenProduct(productCode);
        String key = requireKey(idempotencyKey);
        String hash = hash("CAISHEN|" + productCode);
        FortuneCaishenResponse replay = replay(userId, key, hash, FortuneCaishenResponse.class);
        if (replay != null) {
            return replay.asReplay();
        }
        lockAccount(userId);
        FortuneStateEntity state = lockedState(userId);
        PlayerWalletEntity wallet = lockedWallet(userId);
        debit(wallet, product.priceDiamonds());
        state.activateCaishen(clock.instant(), product.durationSeconds());
        walletRepository.save(wallet);
        stateRepository.save(state);
        FortuneCaishenResponse response =
                new FortuneCaishenResponse(
                        productCode,
                        product.priceDiamonds(),
                        state.getCaishenExpiresAt(),
                        remaining(state.getCaishenExpiresAt(), clock.instant()),
                        ShopWalletResponse.from(wallet),
                        false);
        saveOperation(userId, key, hash, "CAISHEN", response);
        return response;
    }

    private FortuneStateEntity lockedState(UUID userId) {
        return stateRepository
                .findLockedByUserId(userId)
                .orElseGet(() -> stateRepository.save(new FortuneStateEntity(userId, clock.instant())));
    }

    private void lockAccount(UUID userId) {
        operationRepository.acquireOperationLock("fortune-account:" + userId);
    }

    private PlayerWalletEntity lockedWallet(UUID userId) {
        return walletRepository
                .findLockedByUserId(userId)
                .orElseGet(() -> walletRepository.save(new PlayerWalletEntity(userId, 0, 0, 0, 0)));
    }

    private static void debit(PlayerWalletEntity wallet, long amount) {
        try {
            wallet.debitDiamonds(amount);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.FORTUNE_INSUFFICIENT_DIAMONDS, "钻石余额不足");
        }
    }

    private FortuneTreasureProduct randomTreasure() {
        int index = randomIndex.applyAsInt(FortuneCatalog.TREASURES.size());
        if (index < 0 || index >= FortuneCatalog.TREASURES.size()) {
            throw new IllegalStateException("Random treasure index is out of range");
        }
        return FortuneCatalog.TREASURES.get(index);
    }

    private static FortunePrayerProduct prayerProduct(String code) {
        return FortuneCatalog.PRAYERS.stream()
                .filter(product -> product.productCode().equals(code))
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.FORTUNE_PRODUCT_NOT_FOUND,
                                        "求财运商品不存在"));
    }

    private static FortuneCaishenProduct caishenProduct(String code) {
        return FortuneCatalog.CAISHEN.stream()
                .filter(product -> product.productCode().equals(code))
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.FORTUNE_PRODUCT_NOT_FOUND,
                                        "财神商品不存在"));
    }

    private static long discountedCost(long unitPrice, int quantity) {
        return Math.multiplyExact(unitPrice, quantity)
                * FortuneCatalog.QUANTITY_BASIS_POINTS[quantity - 1]
                / 10_000;
    }

    private static FortuneTreasureDrawItem drawItem(
            FortuneTreasureProduct product, FortuneTreasureEntity treasure) {
        return new FortuneTreasureDrawItem(
                product.treasureCode(),
                product.name(),
                product.quality(),
                product.fortuneScore(),
                treasure.getLevel(),
                treasure.getExpiresAt());
    }

    private static FortuneTreasureView treasureView(
            FortuneTreasureEntity treasure, Instant now) {
        FortuneTreasureProduct product =
                FortuneCatalog.TREASURES.stream()
                        .filter(item -> item.treasureCode().equals(treasure.getTreasureCode()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Unknown fortune treasure"));
        return new FortuneTreasureView(
                product.treasureCode(),
                product.name(),
                product.quality(),
                product.fortuneScore(),
                treasure.getLevel(),
                treasure.getExpiresAt(),
                remaining(treasure.getExpiresAt(), now));
    }

    private static long remaining(Instant expiresAt, Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now)
                ? 0
                : now.until(expiresAt, ChronoUnit.SECONDS);
    }

    private String hash(String value) {
        return cryptoService.sha256(value);
    }

    private <T> T replay(UUID userId, String key, String requestHash, Class<T> type) {
        operationRepository.acquireOperationLock("fortune:" + userId + ":" + key);
        FortuneOperationEntity existing =
                operationRepository.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(
                    ErrorCode.FORTUNE_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key 已用于不同的财运操作");
        }
        try {
            return objectMapper.readValue(existing.getResult(), type);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read fortune operation", exception);
        }
    }

    private void saveOperation(
            UUID userId, String key, String requestHash, String type, Object result) {
        try {
            operationRepository.save(
                    new FortuneOperationEntity(
                            userId,
                            key,
                            requestHash,
                            type,
                            objectMapper.writeValueAsString(result),
                            clock.instant()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save fortune operation", exception);
        }
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 128) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不合法");
        }
        return key.trim();
    }
}
