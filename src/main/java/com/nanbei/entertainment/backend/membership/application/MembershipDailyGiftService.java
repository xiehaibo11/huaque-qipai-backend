package com.nanbei.entertainment.backend.membership.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.membership.domain.MembershipDailyGiftClaimEntity;
import com.nanbei.entertainment.backend.membership.infrastructure.MembershipDailyGiftClaimRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class MembershipDailyGiftService {
    private static final List<MembershipDailyGiftOption> OPTIONS =
            List.of(
                    new MembershipDailyGiftOption(
                            1,
                            "至尊帝王礼包",
                            "red",
                            List.of(
                                    new MembershipDailyGiftReward(
                                            "COIN",
                                            "金币",
                                            10_000L,
                                            "至尊帝王1天",
                                            "membership_reward_coin",
                                            null),
                                    new MembershipDailyGiftReward(
                                            "RECORDER",
                                            "记牌器",
                                            5L,
                                            "牛气冲天1天",
                                            "membership_reward_game_card",
                                            1))),
                    new MembershipDailyGiftOption(
                            2,
                            "招财进宝礼包",
                            "green",
                            List.of(
                                    new MembershipDailyGiftReward(
                                            "SHUFFLE_TICKET",
                                            "洗牌券",
                                            1L,
                                            "招财进宝1天",
                                            "membership_reward_shuffle_ticket",
                                            1),
                                    new MembershipDailyGiftReward(
                                            "TREASURE_BOWL",
                                            "聚宝盆",
                                            1L,
                                            "金蟾吞宝1天",
                                            "membership_reward_luck_bead",
                                            1))));

    private final PlayerProfileService profileService;
    private final MembershipStatusService membershipStatusService;
    private final MembershipRewardGrantService rewardGrantService;
    private final PlayerWalletRepository walletRepository;
    private final MembershipDailyGiftClaimRepository claimRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public MembershipDailyGiftService(
            PlayerProfileService profileService,
            MembershipStatusService membershipStatusService,
            MembershipRewardGrantService rewardGrantService,
            PlayerWalletRepository walletRepository,
            MembershipDailyGiftClaimRepository claimRepository,
            ObjectMapper objectMapper) {
        this(
                profileService,
                membershipStatusService,
                rewardGrantService,
                walletRepository,
                claimRepository,
                objectMapper,
                Clock.systemUTC());
    }

    MembershipDailyGiftService(
            PlayerProfileService profileService,
            MembershipStatusService membershipStatusService,
            MembershipRewardGrantService rewardGrantService,
            PlayerWalletRepository walletRepository,
            MembershipDailyGiftClaimRepository claimRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.profileService = profileService;
        this.membershipStatusService = membershipStatusService;
        this.rewardGrantService = rewardGrantService;
        this.walletRepository = walletRepository;
        this.claimRepository = claimRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public MembershipDailyGiftStatus status(UUID userId) {
        MembershipStatus membershipStatus = membershipStatusService.status(userId);
        LocalDate today = today();
        MembershipDailyGiftClaimEntity claim =
                claimRepository.findByUserIdAndClaimedOn(userId, today).orElse(null);
        PlayerWalletEntity wallet = walletRepository.findById(userId).orElse(null);
        return statusFrom(membershipStatus.membershipActive(), claim, today, wallet);
    }

    @Transactional
    public MembershipDailyGiftStatus claim(UUID userId, int giftId) {
        MembershipDailyGiftOption option = option(giftId);
        LocalDate today = today();
        claimRepository.acquireDailyClaimLock(userId + ":" + today);
        profileService.ensureProfile(userId);
        if (!membershipStatusService.isActive(userId)) {
            throw new ApiException(
                    ErrorCode.MEMBERSHIP_DAILY_GIFT_NOT_AVAILABLE,
                    "非会员或会员已过期");
        }
        if (claimRepository.findByUserIdAndClaimedOn(userId, today).isPresent()) {
            throw new ApiException(
                    ErrorCode.MEMBERSHIP_DAILY_GIFT_ALREADY_CLAIMED,
                    "奖励已领取");
        }
        MembershipDailyGiftClaimEntity claim =
                claimRepository.save(
                        new MembershipDailyGiftClaimEntity(
                                userId, today, giftId, rewardsJson(option.rewards())));
        PlayerWalletEntity wallet =
                rewardGrantService.grant(
                        userId,
                        MembershipRewardGrantService.SOURCE_DAILY_GIFT,
                        today + ":" + giftId,
                        grantRewards(option.rewards()));
        return statusFrom(true, claim, today, wallet);
    }

    private String rewardsJson(List<MembershipDailyGiftReward> rewards) {
        try {
            return objectMapper.writeValueAsString(rewards);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize membership daily gift rewards", exception);
        }
    }

    private MembershipDailyGiftOption option(int giftId) {
        return OPTIONS.stream()
                .filter(candidate -> candidate.giftId() == giftId)
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.MEMBERSHIP_DAILY_GIFT_NOT_FOUND,
                                        "礼包配置不存在"));
    }

    private MembershipDailyGiftStatus statusFrom(
            boolean membershipActive,
            MembershipDailyGiftClaimEntity claim,
            LocalDate today,
            PlayerWalletEntity wallet) {
        return new MembershipDailyGiftStatus(
                membershipActive,
                claim != null,
                claim == null ? null : claim.getGiftId(),
                today,
                claim == null ? null : claim.getCreatedAt(),
                OPTIONS,
                wallet == null ? null : walletSnapshot(wallet));
    }

    private static MembershipDailyGiftStatus.WalletSnapshot walletSnapshot(
            PlayerWalletEntity wallet) {
        return new MembershipDailyGiftStatus.WalletSnapshot(
                wallet.getRoomCards(),
                wallet.getBoundRoomCards(),
                wallet.getCoins(),
                wallet.getDiamonds());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private static List<MembershipRewardGrant> grantRewards(
            List<MembershipDailyGiftReward> rewards) {
        return rewards.stream()
                .map(
                        reward ->
                                new MembershipRewardGrant(
                                        reward.code(),
                                        reward.displayName(),
                                        reward.quantity(),
                                        reward.durationDays(),
                                        reward.iconKey()))
                .toList();
    }
}
