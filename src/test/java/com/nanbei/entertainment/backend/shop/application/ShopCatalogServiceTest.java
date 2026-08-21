package com.nanbei.entertainment.backend.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopCatalogServiceTest {
    @Mock ShopProductRepository productRepository;
    @Mock PlayerWalletRepository walletRepository;

    @Test
    void returnsOrderedProductsAndAuthoritativeWallet() {
        UUID userId = UUID.randomUUID();
        ShopProductEntity product =
                ShopProductEntity.exchange(
                        "ROOM_CARD_1",
                        "room_card",
                        "1房卡",
                        "room_card",
                        "DIAMOND",
                        400,
                        "ROOM_CARD",
                        1,
                        401);
        PlayerWalletEntity wallet = new PlayerWalletEntity(userId, 9, 0, 1_835, 500);
        wallet.addCoupons(120);
        when(productRepository.findByEnabledTrueOrderBySortOrderAsc())
                .thenReturn(List.of(product));
        when(walletRepository.findById(userId)).thenReturn(Optional.of(wallet));

        ShopCatalogResponse response =
                new ShopCatalogService(productRepository, walletRepository).load(userId);

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).category()).isEqualTo("room_card");
        assertThat(response.products().get(0).priceCurrency()).isEqualTo("DIAMOND");
        assertThat(response.wallet().roomCards()).isEqualTo(9);
        assertThat(response.wallet().coins()).isEqualTo(1_835);
        assertThat(response.wallet().diamonds()).isEqualTo(500);
        assertThat(response.wallet().coupons()).isEqualTo(120);
    }
}
