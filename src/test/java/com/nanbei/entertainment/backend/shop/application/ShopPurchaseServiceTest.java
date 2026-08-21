package com.nanbei.entertainment.backend.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopInventoryItemRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRewardRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopPurchaseRecordRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopPurchaseServiceTest {
    @Mock ShopProductRepository productRepository;
    @Mock ShopProductRewardRepository rewardRepository;
    @Mock ShopPurchaseRecordRepository purchaseRepository;
    @Mock ShopInventoryItemRepository inventoryRepository;
    @Mock PlayerWalletRepository walletRepository;

    ShopPurchaseService service;
    UUID userId;

    @BeforeEach
    void setUp() {
        service =
                new ShopPurchaseService(
                        productRepository,
                        rewardRepository,
                        purchaseRepository,
                        inventoryRepository,
                        walletRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void atomicallyDebitsDiamondsAndCreditsRoomCards() {
        ShopProductEntity product =
                ShopProductEntity.exchange(
                        "ROOM_CARD_1",
                        "ROOM_CARD",
                        "1房卡",
                        "room_card",
                        "DIAMOND",
                        400,
                        "ROOM_CARD",
                        1,
                        1);
        PlayerWalletEntity wallet = new PlayerWalletEntity(userId, 9, 0, 1_835, 500);
        when(productRepository.findByProductCodeAndEnabledTrue("ROOM_CARD_1"))
                .thenReturn(Optional.of(product));
        when(walletRepository.findLockedByUserId(userId)).thenReturn(Optional.of(wallet));
        when(purchaseRepository.findByUserIdAndIdempotencyKey(userId, "exchange-1"))
                .thenReturn(Optional.empty());
        when(purchaseRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(walletRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ShopPurchaseResponse result = service.exchange(userId, "ROOM_CARD_1", "exchange-1");

        assertThat(result.productCode()).isEqualTo("ROOM_CARD_1");
        assertThat(result.duplicate()).isFalse();
        assertThat(result.wallet().diamonds()).isEqualTo(100);
        assertThat(result.wallet().roomCards()).isEqualTo(10);
        verify(purchaseRepository).save(any());
        verify(walletRepository).save(wallet);
    }

    @Test
    void rejectsInsufficientBalanceWithoutCreatingPurchase() {
        ShopProductEntity product =
                ShopProductEntity.exchange(
                        "COIN_60000",
                        "COIN",
                        "6万金币",
                        "coin_stack",
                        "DIAMOND",
                        600,
                        "COIN",
                        60_000,
                        1);
        PlayerWalletEntity wallet = new PlayerWalletEntity(userId, 0, 0, 0, 599);
        when(productRepository.findByProductCodeAndEnabledTrue("COIN_60000"))
                .thenReturn(Optional.of(product));
        when(walletRepository.findLockedByUserId(userId)).thenReturn(Optional.of(wallet));
        when(purchaseRepository.findByUserIdAndIdempotencyKey(userId, "exchange-2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(userId, "COIN_60000", "exchange-2"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ErrorCode.SHOP_INSUFFICIENT_BALANCE);
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void atomicallyDebitsRoomCardsForRecorderTimeProduct() {
        ShopProductEntity product =
                ShopProductEntity.exchange(
                        "PROP_RECORDER_2_HOURS",
                        "PROP",
                        "记牌器2小时",
                        "recorder",
                        "ROOM_CARD",
                        3,
                        "INVENTORY_PROP",
                        1,
                        707);
        PlayerWalletEntity wallet = new PlayerWalletEntity(userId, 9, 0, 1_835, 0);
        when(productRepository.findByProductCodeAndEnabledTrue("PROP_RECORDER_2_HOURS"))
                .thenReturn(Optional.of(product));
        when(walletRepository.findLockedByUserId(userId)).thenReturn(Optional.of(wallet));
        when(purchaseRepository.findByUserIdAndIdempotencyKey(userId, "recorder-hours"))
                .thenReturn(Optional.empty());
        when(purchaseRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(walletRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ShopPurchaseResponse result =
                service.exchange(userId, "PROP_RECORDER_2_HOURS", "recorder-hours");

        assertThat(result.wallet().roomCards()).isEqualTo(6);
        assertThat(result.wallet().diamonds()).isZero();
        verify(purchaseRepository).save(any());
        verify(walletRepository).save(wallet);
    }

    @Test
    void cnyProductsMustUsePaymentOrders() {
        ShopProductEntity product =
                ShopProductEntity.paid(
                        "DIAMOND_100",
                        "DIAMOND_RECHARGE",
                        "100钻石",
                        "diamond",
                        100,
                        "DIAMOND",
                        100,
                        1,
                        UUID.randomUUID());
        when(productRepository.findByProductCodeAndEnabledTrue("DIAMOND_100"))
                .thenReturn(Optional.of(product));
        when(walletRepository.findLockedByUserId(userId))
                .thenReturn(Optional.of(new PlayerWalletEntity(userId, 0, 0, 0, 0)));
        when(purchaseRepository.findByUserIdAndIdempotencyKey(userId, "exchange-3"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(userId, "DIAMOND_100", "exchange-3"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ErrorCode.SHOP_PAYMENT_REQUIRED);
        verify(purchaseRepository, never()).save(any());
    }
}
