package com.nanbei.entertainment.backend.freedraw.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawActivityEntity;
import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawPrizeEntity;
import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawSessionEntity;
import com.nanbei.entertainment.backend.freedraw.infrastructure.FreeDrawActivityRepository;
import com.nanbei.entertainment.backend.freedraw.infrastructure.FreeDrawPrizeRepository;
import com.nanbei.entertainment.backend.freedraw.infrastructure.FreeDrawSessionRepository;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreeDrawServiceTest {
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTIVITY = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SESSION = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final LocalDate DRAW_DATE = LocalDate.of(2026, 8, 25);

    @Mock FreeDrawActivityRepository activityRepository;
    @Mock FreeDrawPrizeRepository prizeRepository;
    @Mock FreeDrawSessionRepository sessionRepository;
    @Mock PlayerWalletRepository walletRepository;

    private FreeDrawService service;
    private FreeDrawActivityEntity activity;
    private FreeDrawPrizeEntity coinPrize;
    private PlayerWalletEntity wallet;
    private FreeDrawSessionEntity session;

    @BeforeEach
    void setUp() {
        activity =
                new FreeDrawActivityEntity(
                        ACTIVITY,
                        "DAILY_AD_DRAW",
                        "b5f8ceca962d11",
                        "CSJ:945592324",
                        8,
                        true);
        coinPrize =
                new FreeDrawPrizeEntity(
                        UUID.fromString("40000000-0000-0000-0000-000000000001"),
                        ACTIVITY,
                        "COIN",
                        588,
                        "588金币",
                        "coin_bag",
                        100,
                        1,
                        true);
        wallet = new PlayerWalletEntity(USER, 0, 0, 1_000, 20);
        session = FreeDrawSessionEntity.open(SESSION, USER, ACTIVITY, DRAW_DATE, NOW, NOW.plusSeconds(600));
        when(activityRepository.findFirstByEnabledTrueOrderByActivityCodeAsc())
                .thenReturn(Optional.of(activity));
        when(prizeRepository.findByActivityIdAndEnabledTrueOrderByDisplayOrderAsc(ACTIVITY))
                .thenReturn(List.of(coinPrize));
        when(sessionRepository.countByUserIdAndActivityIdAndDrawDateAndStatus(
                        USER, ACTIVITY, DRAW_DATE, FreeDrawSessionEntity.STATUS_GRANTED))
                .thenReturn(0L);
        when(sessionRepository.findLockedById(SESSION)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(FreeDrawSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(walletRepository.findLockedByUserId(USER)).thenReturn(Optional.of(wallet));
        when(walletRepository.findById(USER)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(PlayerWalletEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service =
                new FreeDrawService(
                        activityRepository,
                        prizeRepository,
                        sessionRepository,
                        walletRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        bound -> 0,
                        () -> SESSION);
    }

    @Test
    void statePublishesDisplayPrizesButKeepsWeightsServerSide() {
        FreeDrawResponses.StateResponse state = service.state(USER);

        assertThat(state.remainingDraws()).isEqualTo(8);
        assertThat(state.adPlacementId()).isEqualTo("b5f8ceca962d11");
        assertThat(state.prizes()).singleElement().satisfies(prize -> {
            assertThat(prize.amount()).isEqualTo(588);
            assertThat(prize.displayName()).isEqualTo("588金币");
        });
    }

    @Test
    void verifiedRewardCreditsTheWalletAndPersistsTheChosenPrize() {
        FreeDrawResponses.RewardResponse response =
                service.claim(USER, SESSION, "b5f8ceca962d11", "CSJ:945592324", "show-1");

        assertThat(response.replayed()).isFalse();
        assertThat(response.reward().amount()).isEqualTo(588);
        assertThat(response.wallet().coins()).isEqualTo(1_588);
        assertThat(session.getStatus()).isEqualTo(FreeDrawSessionEntity.STATUS_GRANTED);
        verify(walletRepository).save(wallet);
        verify(sessionRepository).save(session);
    }

    @Test
    void verifiedRewardAcceptsAnotherSourceFromTheOriginalWaterfall() {
        FreeDrawResponses.RewardResponse response =
                service.claim(USER, SESSION, "b5f8ceca962d11", "CSJ:968735997", "show-2");

        assertThat(response.replayed()).isFalse();
        assertThat(response.wallet().coins()).isEqualTo(1_588);
        assertThat(session.getStatus()).isEqualTo(FreeDrawSessionEntity.STATUS_GRANTED);
    }

    @Test
    void duplicateRewardCallbackReplaysWithoutDoubleCredit() {
        FreeDrawResponses.RewardResponse first =
                service.claim(USER, SESSION, "b5f8ceca962d11", "CSJ:945592324", "show-1");
        FreeDrawResponses.RewardResponse replay =
                service.claim(USER, SESSION, "b5f8ceca962d11", "CSJ:945592324", "show-1");

        assertThat(first.wallet().coins()).isEqualTo(1_588);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.reward()).isEqualTo(first.reward());
        assertThat(wallet.getCoins()).isEqualTo(1_588);
        verify(walletRepository, times(1)).save(wallet);
    }

    @Test
    void expiredOrWrongPlacementSessionsNeverCreditTheWallet() {
        FreeDrawSessionEntity expired =
                FreeDrawSessionEntity.open(
                        SESSION, USER, ACTIVITY, DRAW_DATE, NOW.minusSeconds(700), NOW.minusSeconds(1));
        when(sessionRepository.findLockedById(SESSION)).thenReturn(Optional.of(expired));

        assertThatThrownBy(
                        () -> service.claim(USER, SESSION, "b5f8ceca962d11", "CSJ:945592324", "show-1"))
                .isInstanceOf(ApiException.class);
        verify(walletRepository, never()).save(any());

        when(sessionRepository.findLockedById(SESSION)).thenReturn(Optional.of(session));
        assertThatThrownBy(
                        () -> service.claim(USER, SESSION, "wrong-placement", "CSJ:945592324", "show-1"))
                .isInstanceOf(ApiException.class);
        verify(walletRepository, never()).save(any());
    }
}
