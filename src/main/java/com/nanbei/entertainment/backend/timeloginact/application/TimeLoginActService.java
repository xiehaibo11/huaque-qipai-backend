package com.nanbei.entertainment.backend.timeloginact.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.ClaimResponse;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.RewardItem;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.StateResponse;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginActivityEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginClaimEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginClaimFlag;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginRewardStatus;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginSlotEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginSlotSchedule;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginWheelSliceEntity;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginActivityRepository;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginClaimRepository;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginSlotRepository;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginWheelSliceRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时登录有礼的服务端权威规则。时段状态、补领、金币上限、转盘解锁与开奖结果全部在
 * 服务端事务内判定，客户端只渲染结果；奖池概率不下发。
 *
 * <p>这是南北娱乐自研实现，不是恢复出的原版服务端；未闭合项见
 * {@code android/docs/ORIGINAL-TIME-LOGIN-ACT-EVIDENCE.md} 第 9 节。
 */
@Service
public class TimeLoginActService {
    private static final String OPERATION_SLOT = "TIME_LOGIN_SLOT_CLAIM";
    private static final String OPERATION_WHEEL = "TIME_LOGIN_WHEEL_DRAW";

    private final TimeLoginActivityRepository activityRepository;
    private final TimeLoginSlotRepository slotRepository;
    private final TimeLoginWheelSliceRepository wheelSliceRepository;
    private final TimeLoginClaimRepository claimRepository;
    private final TimeLoginOperationLog operationLog;
    private final TimeLoginWallets wallets;
    private final CryptoService cryptoService;
    private final Clock clock;
    private final IntUnaryOperator randomBound;

    @Autowired
    public TimeLoginActService(
            TimeLoginActivityRepository activityRepository,
            TimeLoginSlotRepository slotRepository,
            TimeLoginWheelSliceRepository wheelSliceRepository,
            TimeLoginClaimRepository claimRepository,
            TimeLoginOperationLog operationLog,
            TimeLoginWallets wallets,
            CryptoService cryptoService) {
        this(
                activityRepository,
                slotRepository,
                wheelSliceRepository,
                claimRepository,
                operationLog,
                wallets,
                cryptoService,
                Clock.systemUTC(),
                new SecureRandom()::nextInt);
    }

    TimeLoginActService(
            TimeLoginActivityRepository activityRepository,
            TimeLoginSlotRepository slotRepository,
            TimeLoginWheelSliceRepository wheelSliceRepository,
            TimeLoginClaimRepository claimRepository,
            TimeLoginOperationLog operationLog,
            TimeLoginWallets wallets,
            CryptoService cryptoService,
            Clock clock,
            IntUnaryOperator randomBound) {
        this.activityRepository = activityRepository;
        this.slotRepository = slotRepository;
        this.wheelSliceRepository = wheelSliceRepository;
        this.claimRepository = claimRepository;
        this.operationLog = operationLog;
        this.wallets = wallets;
        this.cryptoService = cryptoService;
        this.clock = clock;
        this.randomBound = randomBound;
    }

    @Transactional(readOnly = true)
    public StateResponse state(UUID userId) {
        TimeLoginDayState day = loadDay(userId);
        return TimeLoginStateAssembler.state(
                day, slices(day), wallets.view(userId), clock.instant().getEpochSecond());
    }

    @Transactional
    public ClaimResponse claimSlot(UUID userId, String idempotencyKey, String rewardId) {
        String key = TimeLoginOperationLog.requireKey(idempotencyKey);
        String requestHash = cryptoService.sha256(OPERATION_SLOT + ':' + rewardId);
        ClaimResponse replayed = operationLog.replay(userId, key, requestHash);
        if (replayed != null) {
            return replayed;
        }
        ClaimResponse response = applySlotClaim(userId, rewardId);
        operationLog.record(userId, key, requestHash, OPERATION_SLOT, response, clock.instant());
        return response;
    }

    @Transactional
    public ClaimResponse drawWheel(UUID userId, String idempotencyKey) {
        String key = TimeLoginOperationLog.requireKey(idempotencyKey);
        String requestHash = cryptoService.sha256(OPERATION_WHEEL);
        ClaimResponse replayed = operationLog.replay(userId, key, requestHash);
        if (replayed != null) {
            return replayed;
        }
        ClaimResponse response = applyWheelDraw(userId);
        operationLog.record(userId, key, requestHash, OPERATION_WHEEL, response, clock.instant());
        return response;
    }

    private ClaimResponse applySlotClaim(UUID userId, String rewardId) {
        TimeLoginDayState day = loadDay(userId);
        int index = day.indexOf(parseRewardId(rewardId));
        if (index < 0) {
            throw new ApiException(ErrorCode.TIME_LOGIN_REWARD_NOT_FOUND, "定时登录奖励不存在");
        }
        TimeLoginRewardStatus status = day.statusAt(index);
        if (status == TimeLoginRewardStatus.REWARDED) {
            return rejected(userId, TimeLoginClaimFlag.ALREADY_CLAIM);
        }
        if (!TimeLoginSlotSchedule.claimable(status)) {
            return rejected(userId, TimeLoginClaimFlag.NOT_IN_TIME);
        }
        PlayerWalletEntity wallet = wallets.locked(userId);
        if (overGoldLimit(wallet, day)) {
            return goldOver(wallet);
        }
        TimeLoginSlotEntity slot = day.orderedSlots().get(index);
        ShopWalletResponse updated =
                wallets.credit(wallet, slot.getRewardType(), slot.getRewardAmount());
        claimRepository.save(
                TimeLoginClaimEntity.forSlot(
                        userId, day.activity().getId(), day.activityDate(), slot, clock.instant()));
        return granted(
                new RewardItem(
                        slot.getRewardType(), slot.getRewardAmount(), slot.getRewardName()),
                null,
                updated);
    }

    private ClaimResponse applyWheelDraw(UUID userId) {
        TimeLoginDayState day = loadDay(userId);
        if (day.wheelDrawCount() > 0) {
            return rejected(userId, TimeLoginClaimFlag.ALREADY_CLAIM);
        }
        if (!day.wheelUnlocked()) {
            return rejected(userId, TimeLoginClaimFlag.WHEEL_CNT_LACK);
        }
        List<TimeLoginWheelSliceEntity> slices = slices(day);
        if (slices.size() != TimeLoginStateAssembler.WHEEL_SLICE_COUNT) {
            throw new ApiException(ErrorCode.TIME_LOGIN_NOT_AVAILABLE, "定时登录转盘配置不完整");
        }
        PlayerWalletEntity wallet = wallets.locked(userId);
        if (overGoldLimit(wallet, day)) {
            return goldOver(wallet);
        }
        TimeLoginWheelSliceEntity slice = TimeLoginWheelPicker.pick(slices, randomBound);
        ShopWalletResponse updated =
                wallets.credit(wallet, slice.getRewardType(), slice.getRewardAmount());
        claimRepository.save(
                TimeLoginClaimEntity.forWheel(
                        userId,
                        day.activity().getId(),
                        day.activityDate(),
                        slice,
                        clock.instant()));
        return granted(
                new RewardItem(
                        slice.getRewardType(), slice.getRewardAmount(), slice.getRewardName()),
                slice.getSliceIndex(),
                updated);
    }

    private TimeLoginDayState loadDay(UUID userId) {
        TimeLoginActivityEntity activity =
                activityRepository
                        .findFirstByEnabledTrueOrderByActivityCodeAsc()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.TIME_LOGIN_NOT_AVAILABLE,
                                                "当前没有开启的定时登录活动"));
        List<TimeLoginSlotEntity> slots =
                slotRepository.findByActivityIdOrderBySlotOrderAsc(activity.getId());
        if (slots.isEmpty()) {
            throw new ApiException(ErrorCode.TIME_LOGIN_NOT_AVAILABLE, "定时登录时段未配置");
        }
        Instant now = clock.instant();
        List<TimeLoginClaimEntity> claims =
                claimRepository.findByUserIdAndActivityIdAndActivityDate(
                        userId,
                        activity.getId(),
                        TimeLoginSlotSchedule.activityDate(
                                now, activity.getDayBoundarySecond()));
        return TimeLoginDayState.of(activity, slots, claims, now);
    }

    private List<TimeLoginWheelSliceEntity> slices(TimeLoginDayState day) {
        return wheelSliceRepository.findByActivityIdOrderBySliceIndexAsc(day.activity().getId());
    }

    /** 原版页脚提示「携带金币超过 %s 不可领奖」，判定的是当前持有金币。 */
    private static boolean overGoldLimit(PlayerWalletEntity wallet, TimeLoginDayState day) {
        return wallet.getCoins() > day.activity().getGoldOver();
    }

    private static ClaimResponse goldOver(PlayerWalletEntity wallet) {
        return new ClaimResponse(
                TimeLoginClaimFlag.GOLD_OVER.wireValue(),
                List.of(),
                null,
                ShopWalletResponse.from(wallet));
    }

    private static ClaimResponse granted(
            RewardItem reward, Integer wheelSliceIndex, ShopWalletResponse wallet) {
        return new ClaimResponse(
                TimeLoginClaimFlag.SUCCESS.wireValue(),
                List.of(reward),
                wheelSliceIndex,
                wallet);
    }

    private ClaimResponse rejected(UUID userId, TimeLoginClaimFlag flag) {
        return new ClaimResponse(flag.wireValue(), List.of(), null, wallets.view(userId));
    }

    private static UUID parseRewardId(String rewardId) {
        try {
            return UUID.fromString(rewardId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.TIME_LOGIN_REWARD_NOT_FOUND, "定时登录奖励不存在");
        }
    }
}
