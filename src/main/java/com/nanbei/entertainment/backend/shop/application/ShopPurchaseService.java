package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.domain.ShopInventoryItemEntity;
import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;
import com.nanbei.entertainment.backend.shop.domain.ShopProductRewardEntity;
import com.nanbei.entertainment.backend.shop.domain.ShopPurchaseRecordEntity;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopInventoryItemRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRewardRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopPurchaseRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopPurchaseService {
    private static final ZoneId CHINA_TIME = ZoneId.of("Asia/Shanghai");

    private final ShopProductRepository productRepository;
    private final ShopProductRewardRepository rewardRepository;
    private final ShopPurchaseRecordRepository purchaseRepository;
    private final ShopInventoryItemRepository inventoryRepository;
    private final PlayerWalletRepository walletRepository;

    public ShopPurchaseService(
            ShopProductRepository productRepository,
            ShopProductRewardRepository rewardRepository,
            ShopPurchaseRecordRepository purchaseRepository,
            ShopInventoryItemRepository inventoryRepository,
            PlayerWalletRepository walletRepository) {
        this.productRepository = productRepository;
        this.rewardRepository = rewardRepository;
        this.purchaseRepository = purchaseRepository;
        this.inventoryRepository = inventoryRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional
    public ShopPurchaseResponse exchange(
            UUID userId, String productCode, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        PlayerWalletEntity wallet = lockedWallet(userId);
        var duplicate =
                purchaseRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (duplicate.isPresent()) {
            return response(duplicate.get(), wallet, true);
        }
        ShopProductEntity product = product(productCode);
        if ("CNY".equals(product.getPriceCurrency())) {
            throw new ApiException(
                    ErrorCode.SHOP_PAYMENT_REQUIRED, "人民币商品必须通过支付订单购买");
        }
        int purchaseNumber = enforceLimits(userId, product);
        debit(wallet, product);
        grant(userId, wallet, product, purchaseNumber);
        ShopPurchaseRecordEntity purchase =
                purchaseRepository.save(
                        new ShopPurchaseRecordEntity(
                                userId, product, null, idempotencyKey));
        walletRepository.save(wallet);
        return response(purchase, wallet, false);
    }

    @Transactional
    public void fulfillPaidOrder(UUID userId, UUID orderId, UUID paymentProductId) {
        if (purchaseRepository.existsByOrderId(orderId)) {
            return;
        }
        productRepository
                .findByPaymentProductIdAndEnabledTrue(paymentProductId)
                .filter(product -> !"time_membership".equalsIgnoreCase(product.getCategory()))
                .ifPresent(product -> fulfillPaidProduct(userId, orderId, product));
    }

    private void fulfillPaidProduct(
            UUID userId, UUID orderId, ShopProductEntity product) {
        PlayerWalletEntity wallet = lockedWallet(userId);
        if (purchaseRepository.existsByOrderId(orderId)) {
            return;
        }
        int purchaseNumber = enforceLimits(userId, product);
        grant(userId, wallet, product, purchaseNumber);
        purchaseRepository.save(
                new ShopPurchaseRecordEntity(
                        userId, product, orderId, "PAYMENT:" + orderId));
        walletRepository.save(wallet);
    }

    private PlayerWalletEntity lockedWallet(UUID userId) {
        return walletRepository
                .findLockedByUserId(userId)
                .orElseGet(
                        () -> walletRepository.save(
                                new PlayerWalletEntity(userId, 0, 0, 0, 0)));
    }

    private ShopProductEntity product(String productCode) {
        return productRepository
                .findByProductCodeAndEnabledTrue(productCode)
                .orElseThrow(
                        () -> new ApiException(
                                ErrorCode.SHOP_PRODUCT_NOT_FOUND, "商城商品不存在或已下架"));
    }

    private void debit(PlayerWalletEntity wallet, ShopProductEntity product) {
        try {
            switch (product.getPriceCurrency()) {
                case "DIAMOND" -> wallet.debitDiamonds(product.getPriceAmount());
                case "ROOM_CARD" -> wallet.debitRoomCards(product.getPriceAmount());
                case "COUPON" -> wallet.debitCoupons(product.getPriceAmount());
                case "FREE" -> {
                    if (product.getPriceAmount() != 0) {
                        throw new IllegalArgumentException("invalid free price");
                    }
                }
                default -> throw new ApiException(
                        ErrorCode.SHOP_PAYMENT_REQUIRED, "不支持直接兑换的支付币种");
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.SHOP_INSUFFICIENT_BALANCE, "余额不足");
        }
    }

    private void grant(
            UUID userId,
            PlayerWalletEntity wallet,
            ShopProductEntity product,
            int purchaseNumber) {
        var rewards = rewardRepository.findForPurchase(product.getId(), purchaseNumber);
        if (rewards.isEmpty()) {
            grantReward(
                    userId,
                    wallet,
                    product.getRewardType(),
                    product.getRewardQuantity(),
                    product.getProductCode());
            return;
        }
        for (ShopProductRewardEntity reward : rewards) {
            grantReward(
                    userId,
                    wallet,
                    reward.getRewardType(),
                    reward.getRewardQuantity(),
                    reward.getItemCode() == null
                            ? product.getProductCode()
                            : reward.getItemCode());
        }
    }

    private void grantReward(
            UUID userId,
            PlayerWalletEntity wallet,
            String rewardType,
            long rewardQuantity,
            String itemCode) {
        switch (rewardType) {
            case "DIAMOND" -> wallet.addDiamonds(rewardQuantity);
            case "ROOM_CARD" -> wallet.addRoomCards(rewardQuantity);
            case "COIN" -> wallet.addCoins(rewardQuantity);
            case "COUPON" -> wallet.addCoupons(rewardQuantity);
            default -> grantInventory(userId, itemCode, rewardQuantity);
        }
    }

    private void grantInventory(UUID userId, String itemCode, long quantity) {
        var existing = inventoryRepository.findLocked(userId, itemCode);
        ShopInventoryItemEntity item;
        if (existing.isPresent()) {
            item = existing.get();
            item.addQuantity(quantity);
        } else {
            item = new ShopInventoryItemEntity(userId, itemCode, quantity);
        }
        inventoryRepository.save(item);
    }

    private int enforceLimits(UUID userId, ShopProductEntity product) {
        long lifetimeCount =
                purchaseRepository.countByUserIdAndProductId(userId, product.getId());
        Integer lifetimeLimit = product.getLifetimeLimit();
        if (lifetimeLimit != null
                && lifetimeLimit > 0
                && lifetimeCount >= lifetimeLimit) {
            throw new ApiException(ErrorCode.SHOP_DAILY_LIMIT_REACHED, "该商品购买次数已用完");
        }
        Integer limit = product.getDailyLimit();
        if (limit == null || limit <= 0) {
            return boundedPurchaseNumber(lifetimeCount + 1);
        }
        Instant start = LocalDate.now(CHINA_TIME).atStartOfDay(CHINA_TIME).toInstant();
        long count = purchaseRepository
                .countByUserIdAndProductIdAndCreatedAtGreaterThanEqual(
                        userId, product.getId(), start);
        if (count >= limit) {
            throw new ApiException(ErrorCode.SHOP_DAILY_LIMIT_REACHED, "今日领取次数已用完");
        }
        return boundedPurchaseNumber(count + 1);
    }

    private static int boundedPurchaseNumber(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static ShopPurchaseResponse response(
            ShopPurchaseRecordEntity purchase,
            PlayerWalletEntity wallet,
            boolean duplicate) {
        return new ShopPurchaseResponse(
                purchase.getId().toString(),
                purchase.getProductCode(),
                purchase.getStatus(),
                duplicate,
                ShopWalletResponse.from(wallet));
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不合法");
        }
    }
}
