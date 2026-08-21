package com.nanbei.entertainment.backend.fortune.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.fortune.domain.FortuneStateEntity;
import com.nanbei.entertainment.backend.fortune.domain.FortuneTreasureEntity;
import com.nanbei.entertainment.backend.fortune.infrastructure.FortuneOperationRepository;
import com.nanbei.entertainment.backend.fortune.infrastructure.FortuneStateRepository;
import com.nanbei.entertainment.backend.fortune.infrastructure.FortuneTreasureRepository;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class FortuneServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    @Mock FortuneStateRepository stateRepository;
    @Mock FortuneTreasureRepository treasureRepository;
    @Mock FortuneOperationRepository operationRepository;
    @Mock PlayerWalletRepository walletRepository;

    FortuneService service;
    FortuneStateEntity state;
    PlayerWalletEntity wallet;

    @BeforeEach
    void setUp() {
        service =
                new FortuneService(
                        stateRepository,
                        treasureRepository,
                        operationRepository,
                        walletRepository,
                        new CryptoService(),
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        bound -> 0);
        state = new FortuneStateEntity(USER_ID, NOW);
        wallet = new PlayerWalletEntity(USER_ID, 10_000, 0, 600, 1_000);
    }

    @Test
    void stateReturnsServerOwnedCatalogWalletAndActiveItems() {
        when(stateRepository.findById(USER_ID)).thenReturn(Optional.of(state));
        when(walletRepository.findById(USER_ID)).thenReturn(Optional.of(wallet));
        when(treasureRepository.findByUserIdOrderByTreasureCode(USER_ID)).thenReturn(List.of());

        FortuneStateResponse response = service.state(USER_ID);

        assertThat(response.prayerProducts()).hasSize(12);
        assertThat(response.treasureProducts()).hasSize(16);
        assertThat(response.caishenProducts()).hasSize(3);
        assertThat(response.wallet().diamonds()).isEqualTo(1_000);
        assertThat(response.treasureOneDrawPriceDiamonds()).isEqualTo(100);
        assertThat(response.treasureFiveDrawPriceDiamonds()).isEqualTo(450);
        assertThat(response.treasureFiveDrawDiscountTenths()).isEqualTo(9);
        assertThat(response.treasureProducts())
                .extracting(FortuneTreasureProduct::name)
                .containsExactly(
                        "手串", "宝瓶", "金元宝", "玉佩",
                        "宝石戒指", "聚宝葫芦", "金算盘", "金猪拱财",
                        "铜钱串", "阴阳宝镜", "转运珠", "玉如意",
                        "招财金猫", "金钱树", "聚宝盆", "金蟾吐宝");
        assertThat(response.treasureProducts())
                .extracting(FortuneTreasureProduct::quality)
                .containsExactly(
                        "NORMAL", "NORMAL", "NORMAL", "NORMAL",
                        "GOOD", "GOOD", "GOOD", "GOOD",
                        "SUPER", "SUPER", "SUPER", "SUPER",
                        "SPECIAL", "SPECIAL", "SPECIAL", "SPECIAL");
        assertThat(response.treasureProducts())
                .extracting(FortuneTreasureProduct::fortuneScore)
                .containsExactly(
                        60, 60, 60, 60,
                        80, 80, 80, 80,
                        168, 168, 168, 168,
                        666, 666, 666, 666);
    }

    @Test
    void oneTreasureDrawDebitsOneHundredDiamondsAndExpiresInThreeHours() {
        arrangeWrite("draw-1");
        when(treasureRepository.findByUserIdAndTreasureCode(USER_ID, "TREASURE_01"))
                .thenReturn(Optional.empty());
        when(treasureRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FortuneTreasureDrawResponse response = service.drawTreasures(USER_ID, "draw-1", 1);

        verify(operationRepository).acquireOperationLock("fortune-account:" + USER_ID);
        assertThat(wallet.getDiamonds()).isEqualTo(900);
        assertThat(response.draws()).singleElement().satisfies(draw -> {
            assertThat(draw.treasureCode()).isEqualTo("TREASURE_01");
            assertThat(draw.level()).isEqualTo(1);
            assertThat(draw.expiresAt()).isEqualTo(NOW.plusSeconds(3 * 60 * 60));
        });
    }

    @Test
    void fiveDrawsUseOriginalClientDiscountAndDuplicateCapsAtTen() {
        arrangeWrite("draw-5");
        FortuneTreasureEntity existing =
                new FortuneTreasureEntity(USER_ID, "TREASURE_01", NOW);
        for (int index = 0; index < 9; index++) {
            existing.refresh(NOW);
        }
        when(treasureRepository.findByUserIdAndTreasureCode(USER_ID, "TREASURE_01"))
                .thenReturn(Optional.of(existing));
        when(treasureRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FortuneTreasureDrawResponse response = service.drawTreasures(USER_ID, "draw-5", 5);

        assertThat(wallet.getDiamonds()).isEqualTo(550);
        assertThat(response.draws()).hasSize(5).allSatisfy(draw -> assertThat(draw.level()).isEqualTo(10));
        assertThat(existing.getExpiresAt()).isEqualTo(NOW.plusSeconds(3 * 60 * 60));
    }

    @Test
    void caishenActivationExtendsExistingAuthorityExpiry() {
        state.activateCaishen(NOW, 3_600);
        arrangeWrite("caishen-1");

        FortuneCaishenResponse response =
                service.activateCaishen(USER_ID, "caishen-1", "CAISHEN_1H");

        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(7_200));
        assertThat(response.remainingSeconds()).isEqualTo(7_200);
    }

    @Test
    void insufficientDiamondsRollsBackAsBusinessError() {
        wallet = new PlayerWalletEntity(USER_ID, 10_000, 0, 600, 0);
        arrangeWrite("draw-empty");

        assertThatThrownBy(() -> service.drawTreasures(USER_ID, "draw-empty", 1))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORTUNE_INSUFFICIENT_DIAMONDS);
    }

    private void arrangeWrite(String key) {
        when(operationRepository.findByUserIdAndIdempotencyKey(USER_ID, key))
                .thenReturn(Optional.empty());
        when(stateRepository.findLockedByUserId(USER_ID)).thenReturn(Optional.of(state));
        when(walletRepository.findLockedByUserId(USER_ID)).thenReturn(Optional.of(wallet));
    }
}
