package com.nanbei.entertainment.backend.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.membership.application.GoldMembershipCardService;
import com.nanbei.entertainment.backend.shop.domain.ShopInventoryItemEntity;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopPurchaseServiceTest {
    @Mock ShopProductRepository productRepository;
    @Mock ShopProductRewardRepository rewardRepository;
    @Mock ShopPurchaseRecordRepository purchaseRepository;
    @Mock ShopInventoryItemRepository inventoryRepository;
    @Mock PlayerWalletRepository walletRepository;
    @Mock GoldMembershipCardService goldMembershipCardService;

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
                        walletRepository,
                        goldMembershipCardService);
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
    void chatVoicePurchaseDebitsDiamondsAndPersistsTheVoicePack() {
        ShopProductEntity product =
                ShopProductEntity.exchange(
                        "CHAT_VOICE_XIAOGU_1_DAY",
                        "interaction",
                        "小谷专属语音包1天",
                        "voice",
                        "DIAMOND",
                        100,
                        "INTERACTION_PROP",
                        1,
                        809);
        PlayerWalletEntity wallet = new PlayerWalletEntity(userId, 0, 0, 0, 500);
        when(productRepository.findByProductCodeAndEnabledTrue("CHAT_VOICE_XIAOGU_1_DAY"))
                .thenReturn(Optional.of(product));
        when(walletRepository.findLockedByUserId(userId)).thenReturn(Optional.of(wallet));
        when(purchaseRepository.findByUserIdAndIdempotencyKey(userId, "chat-voice"))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findLocked(userId, "CHAT_VOICE_XIAOGU_1_DAY"))
                .thenReturn(Optional.empty());
        when(purchaseRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(walletRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ShopPurchaseResponse result =
                service.exchange(userId, "CHAT_VOICE_XIAOGU_1_DAY", "chat-voice");

        assertThat(result.wallet().diamonds()).isEqualTo(400);
        ArgumentCaptor<ShopInventoryItemEntity> inventory =
                ArgumentCaptor.forClass(ShopInventoryItemEntity.class);
        verify(inventoryRepository).save(inventory.capture());
        assertThat(inventory.getValue().getItemCode())
                .isEqualTo("CHAT_VOICE_XIAOGU_1_DAY");
        assertThat(inventory.getValue().getQuantity()).isEqualTo(1);
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

    @Test
    void goldMembershipRewardActivatesEntitlementInsteadOfInventory() {
        ShopProductEntity product =
                ShopProductEntity.exchange(
                        "GOLD_MEMBER_WEEK",
                        "gold_membership",
                        "会员周卡",
                        "coin_gift",
                        "DIAMOND",
                        1800,
                        "GOLD_MEMBERSHIP_DAY",
                        7,
                        601);
        PlayerWalletEntity wallet =
                new PlayerWalletEntity(userId, 0, 0, 0, 2_000);
        when(productRepository.findByProductCodeAndEnabledTrue("GOLD_MEMBER_WEEK"))
                .thenReturn(Optional.of(product));
        when(walletRepository.findLockedByUserId(userId))
                .thenReturn(Optional.of(wallet));
        when(purchaseRepository.findByUserIdAndIdempotencyKey(userId, "gold-week"))
                .thenReturn(Optional.empty());
        when(purchaseRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(walletRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ShopPurchaseResponse result =
                service.exchange(userId, "GOLD_MEMBER_WEEK", "gold-week");

        assertThat(result.wallet().diamonds()).isEqualTo(200);
        verify(goldMembershipCardService)
                .activate(userId, "GOLD_MEMBER_WEEK", 7);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void legacyValueMonthDoesNotExtendWeekOrMonthCard() {
        ShopProductEntity product =
                ShopProductEntity.exchange(
                        "GOLD_MEMBER_VALUE_MONTH",
                        "gold_membership",
                        "超值月卡",
                        "treasure_pot",
                        "DIAMOND",
                        2800,
                        "GOLD_MEMBERSHIP_DAY",
                        30,
                        603);
        PlayerWalletEntity wallet =
                new PlayerWalletEntity(userId, 0, 0, 0, 3_000);
        when(productRepository.findByProductCodeAndEnabledTrue(
                        "GOLD_MEMBER_VALUE_MONTH"))
                .thenReturn(Optional.of(product));
        when(walletRepository.findLockedByUserId(userId))
                .thenReturn(Optional.of(wallet));
        when(purchaseRepository.findByUserIdAndIdempotencyKey(
                        userId, "legacy-value-month"))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findLocked(
                        userId, "GOLD_MEMBER_VALUE_MONTH"))
                .thenReturn(Optional.empty());
        when(purchaseRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(walletRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.exchange(
                userId, "GOLD_MEMBER_VALUE_MONTH", "legacy-value-month");

        verifyNoInteractions(goldMembershipCardService);
        verify(inventoryRepository).save(any());
    }
}
