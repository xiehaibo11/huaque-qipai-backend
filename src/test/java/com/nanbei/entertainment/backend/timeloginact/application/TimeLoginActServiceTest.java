package com.nanbei.entertainment.backend.timeloginact.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.ClaimResponse;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.StateResponse;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginActivityEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginClaimEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginSlotEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginWheelSliceEntity;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginActivityRepository;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginClaimRepository;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginOperationRepository;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginSlotRepository;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginWheelSliceRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

/** 定时登录领奖链路的服务端权威判定。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeLoginActServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTIVITY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MORNING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID NOON_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID EVENING_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    /** 2026-08-14 11:00 中国时区，落在午间 09:00-16:00 内。 */
    private static final Instant NOON_MOMENT = Instant.parse("2026-08-14T03:00:00Z");

    @Mock TimeLoginActivityRepository activityRepository;
    @Mock TimeLoginSlotRepository slotRepository;
    @Mock TimeLoginWheelSliceRepository wheelSliceRepository;
    @Mock TimeLoginClaimRepository claimRepository;
    @Mock TimeLoginOperationRepository operationRepository;
    @Mock PlayerWalletRepository walletRepository;

    TimeLoginActService service;
    TimeLoginActivityEntity activity;
    List<TimeLoginSlotEntity> slots;
    PlayerWalletEntity wallet;

    @BeforeEach
    void setUp() {
        activity = TimeLoginFixtures.activity(ACTIVITY_ID, 50_000, 0, 3);
        slots =
                List.of(
                        TimeLoginFixtures.slot(MORNING_ID, ACTIVITY_ID, 1, 82800, 32400, 1000),
                        TimeLoginFixtures.slot(NOON_ID, ACTIVITY_ID, 2, 32400, 57600, 1000),
                        TimeLoginFixtures.slot(EVENING_ID, ACTIVITY_ID, 3, 57600, 82800, 1200));
        wallet = new PlayerWalletEntity(USER_ID, 0, 0, 1_000, 0);
        when(activityRepository.findFirstByEnabledTrueOrderByActivityCodeAsc())
                .thenReturn(Optional.of(activity));
        when(slotRepository.findByActivityIdOrderBySlotOrderAsc(ACTIVITY_ID)).thenReturn(slots);
        when(claimRepository.findByUserIdAndActivityIdAndActivityDate(any(), any(), any()))
                .thenReturn(List.of());
        when(wheelSliceRepository.findByActivityIdOrderBySliceIndexAsc(ACTIVITY_ID))
                .thenReturn(wheelSlices());
        when(walletRepository.findLockedByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        when(walletRepository.findById(USER_ID)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(PlayerWalletEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(operationRepository.findByUserIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        service = service(NOON_MOMENT, bound -> 0);
    }

    private TimeLoginActService service(Instant now, java.util.function.IntUnaryOperator random) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new TimeLoginActService(
                activityRepository,
                slotRepository,
                wheelSliceRepository,
                claimRepository,
                new TimeLoginOperationLog(operationRepository, objectMapper),
                new TimeLoginWallets(walletRepository),
                new CryptoService(),
                Clock.fixed(now, ZoneOffset.UTC),
                random);
    }

    private static List<TimeLoginWheelSliceEntity> wheelSlices() {
        return List.of(
                TimeLoginFixtures.slice(ACTIVITY_ID, 0, "COIN", 2_000, 240),
                TimeLoginFixtures.slice(ACTIVITY_ID, 1, "DIAMOND", 5, 120),
                TimeLoginFixtures.slice(ACTIVITY_ID, 2, "COIN", 5_000, 180),
                TimeLoginFixtures.slice(ACTIVITY_ID, 3, "COIN", 1, 60),
                TimeLoginFixtures.slice(ACTIVITY_ID, 4, "COIN", 10_000, 120),
                TimeLoginFixtures.slice(ACTIVITY_ID, 5, "DIAMOND", 20, 40),
                TimeLoginFixtures.slice(ACTIVITY_ID, 6, "COIN", 50_000, 30),
                TimeLoginFixtures.slice(ACTIVITY_ID, 7, "COIN", 100_000, 10));
    }

    @Test
    void stateOrdersSlotsByNormalisedStartAndReportsAuthoritativeFlags() {
        StateResponse state = service.state(USER_ID);
        assertThat(state.loginRewards()).hasSize(3);
        assertThat(state.loginRewards().get(0).rewardId()).isEqualTo(MORNING_ID.toString());
        assertThat(state.loginRewards().get(0).startTime()).isEqualTo(82800);
        assertThat(state.loginRewards().get(0).rewardFlag()).isEqualTo("OverTime");
        assertThat(state.loginRewards().get(1).rewardFlag()).isEqualTo("CanReward");
        assertThat(state.loginRewards().get(2).rewardFlag()).isEqualTo("NotInTime");
        assertThat(state.goldOver()).isEqualTo(50_000);
        assertThat(state.daySecond()).isEqualTo(39600);
    }

    @Test
    void stateExposesTheEightSliceWheelWithoutWeights() {
        StateResponse state = service.state(USER_ID);
        assertThat(state.wheelReward()).isNotNull();
        assertThat(state.wheelReward().props()).hasSize(8);
        assertThat(state.wheelReward().wheelCnt()).isEqualTo(3);
        assertThat(state.wheelReward().curCnt()).isZero();
    }

    @Test
    void claimingTheOpenSlotCreditsTheAuthoritativeWallet() {
        ClaimResponse response = service.claimSlot(USER_ID, "key-1", NOON_ID.toString());
        assertThat(response.claimFlag()).isEqualTo("Success");
        assertThat(response.props()).singleElement().satisfies(item -> {
            assertThat(item.propId()).isEqualTo("COIN");
            assertThat(item.propCnt()).isEqualTo(1000);
        });
        assertThat(response.wallet().coins()).isEqualTo(2_000);
        ArgumentCaptor<TimeLoginClaimEntity> captor =
                ArgumentCaptor.forClass(TimeLoginClaimEntity.class);
        verify(claimRepository).save(captor.capture());
        assertThat(captor.getValue().getSlotId()).isEqualTo(NOON_ID);
        assertThat(captor.getValue().getActivityDate()).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void claimingAnExpiredSlotIsRejectedWithoutTouchingTheWallet() {
        ClaimResponse response = service.claimSlot(USER_ID, "key-2", MORNING_ID.toString());
        assertThat(response.claimFlag()).isEqualTo("Not_In_Time");
        assertThat(response.props()).isEmpty();
        verify(claimRepository, never()).save(any());
    }

    @Test
    void claimingAFutureSlotIsRejected() {
        ClaimResponse response = service.claimSlot(USER_ID, "key-3", EVENING_ID.toString());
        assertThat(response.claimFlag()).isEqualTo("Not_In_Time");
        verify(claimRepository, never()).save(any());
    }

    @Test
    void reclaimingTheSameSlotReportsAlreadyClaim() {
        when(claimRepository.findByUserIdAndActivityIdAndActivityDate(any(), any(), any()))
                .thenReturn(
                        List.of(
                                TimeLoginClaimEntity.forSlot(
                                        USER_ID,
                                        ACTIVITY_ID,
                                        LocalDate.of(2026, 8, 14),
                                        slots.get(1),
                                        NOON_MOMENT)));
        ClaimResponse response = service.claimSlot(USER_ID, "key-4", NOON_ID.toString());
        assertThat(response.claimFlag()).isEqualTo("Already_Claim");
        verify(claimRepository, never()).save(any());
    }

    @Test
    void carryingMoreCoinsThanTheLimitBlocksTheClaim() {
        wallet.addCoins(60_000);
        ClaimResponse response = service.claimSlot(USER_ID, "key-5", NOON_ID.toString());
        assertThat(response.claimFlag()).isEqualTo("Gold_Over");
        assertThat(response.props()).isEmpty();
        verify(claimRepository, never()).save(any());
    }

    @Test
    void wheelDrawIsRejectedUntilTheUnlockCountIsReached() {
        ClaimResponse response = service.drawWheel(USER_ID, "key-6");
        assertThat(response.claimFlag()).isEqualTo("Wheel_Cnt_Lack");
        verify(claimRepository, never()).save(any());
    }

    @Test
    void wheelDrawGrantsTheServerChosenSliceOnce() {
        when(claimRepository.findByUserIdAndActivityIdAndActivityDate(any(), any(), any()))
                .thenReturn(
                        List.of(
                                claimFor(slots.get(0)),
                                claimFor(slots.get(1)),
                                claimFor(slots.get(2))));
        // 权重前缀：240/360/540/600/720/760/790/800，roll=550 落在下标 3。
        service = service(NOON_MOMENT, bound -> 550);
        ClaimResponse response = service.drawWheel(USER_ID, "key-7");
        assertThat(response.claimFlag()).isEqualTo("Success");
        assertThat(response.wheelSliceIndex()).isEqualTo(3);
        verify(claimRepository).save(any(TimeLoginClaimEntity.class));
    }

    @Test
    void secondWheelDrawInTheSameDayReportsAlreadyClaim() {
        when(claimRepository.findByUserIdAndActivityIdAndActivityDate(any(), any(), any()))
                .thenReturn(
                        List.of(
                                claimFor(slots.get(0)),
                                claimFor(slots.get(1)),
                                claimFor(slots.get(2)),
                                TimeLoginClaimEntity.forWheel(
                                        USER_ID,
                                        ACTIVITY_ID,
                                        LocalDate.of(2026, 8, 14),
                                        wheelSlices().get(0),
                                        NOON_MOMENT)));
        ClaimResponse response = service.drawWheel(USER_ID, "key-8");
        assertThat(response.claimFlag()).isEqualTo("Already_Claim");
        verify(claimRepository, never()).save(any());
    }

    @Test
    void wheelProgressResetsAfterTheDrawIsConsumed() {
        when(claimRepository.findByUserIdAndActivityIdAndActivityDate(any(), any(), any()))
                .thenReturn(
                        List.of(
                                claimFor(slots.get(0)),
                                claimFor(slots.get(1)),
                                claimFor(slots.get(2)),
                                TimeLoginClaimEntity.forWheel(
                                        USER_ID,
                                        ACTIVITY_ID,
                                        LocalDate.of(2026, 8, 14),
                                        wheelSlices().get(0),
                                        NOON_MOMENT)));
        assertThat(service.state(USER_ID).wheelReward().curCnt()).isZero();
    }

    @Test
    void unknownRewardIdIsRejectedBeforeAnyWalletWork() {
        assertThat(
                        org.assertj.core.api.Assertions.catchThrowable(
                                () -> service.claimSlot(USER_ID, "key-9", "not-a-uuid")))
                .isInstanceOf(com.nanbei.entertainment.backend.common.error.ApiException.class);
        verify(walletRepository, never()).findLockedByUserId(eq(USER_ID));
    }

    private TimeLoginClaimEntity claimFor(TimeLoginSlotEntity slot) {
        return TimeLoginClaimEntity.forSlot(
                USER_ID, ACTIVITY_ID, LocalDate.of(2026, 8, 14), slot, NOON_MOMENT);
    }
}
