package com.nanbei.entertainment.backend.freedraw.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.freedraw.application.FreeDrawResponses.PrizeView;
import com.nanbei.entertainment.backend.freedraw.application.FreeDrawResponses.RewardResponse;
import com.nanbei.entertainment.backend.freedraw.application.FreeDrawResponses.SessionResponse;
import com.nanbei.entertainment.backend.freedraw.application.FreeDrawResponses.StateResponse;
import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawActivityEntity;
import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawPrizeEntity;
import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawSessionEntity;
import com.nanbei.entertainment.backend.freedraw.infrastructure.FreeDrawActivityRepository;
import com.nanbei.entertainment.backend.freedraw.infrastructure.FreeDrawPrizeRepository;
import com.nanbei.entertainment.backend.freedraw.infrastructure.FreeDrawSessionRepository;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FreeDrawService {
    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long SESSION_TTL_SECONDS = 600;
    private static final Set<String> ORIGINAL_REWARDED_SOURCES =
            Set.of(
                    "CSJ:945592324",
                    "CSJ:968735997",
                    "CSJ:969006737",
                    "CSJ:969188081",
                    "CSJ:968735998",
                    "CSJ:968731450");

    private final FreeDrawActivityRepository activityRepository;
    private final FreeDrawPrizeRepository prizeRepository;
    private final FreeDrawSessionRepository sessionRepository;
    private final PlayerWalletRepository walletRepository;
    private final Clock clock;
    private final IntUnaryOperator randomBound;
    private final Supplier<UUID> idSupplier;

    @Autowired
    public FreeDrawService(
            FreeDrawActivityRepository activityRepository,
            FreeDrawPrizeRepository prizeRepository,
            FreeDrawSessionRepository sessionRepository,
            PlayerWalletRepository walletRepository) {
        this(
                activityRepository,
                prizeRepository,
                sessionRepository,
                walletRepository,
                Clock.systemUTC(),
                new SecureRandom()::nextInt,
                UUID::randomUUID);
    }

    FreeDrawService(
            FreeDrawActivityRepository activityRepository,
            FreeDrawPrizeRepository prizeRepository,
            FreeDrawSessionRepository sessionRepository,
            PlayerWalletRepository walletRepository,
            Clock clock,
            IntUnaryOperator randomBound,
            Supplier<UUID> idSupplier) {
        this.activityRepository = activityRepository;
        this.prizeRepository = prizeRepository;
        this.sessionRepository = sessionRepository;
        this.walletRepository = walletRepository;
        this.clock = clock;
        this.randomBound = randomBound;
        this.idSupplier = idSupplier;
    }

    @Transactional(readOnly = true)
    public StateResponse state(UUID userId) {
        FreeDrawActivityEntity activity = activity();
        List<FreeDrawPrizeEntity> prizes = prizes(activity);
        int completed = completed(userId, activity);
        return new StateResponse(
                activity.getActivityCode(),
                activity.getAdPlacementId(),
                activity.getDailyLimit(),
                completed,
                Math.max(0, activity.getDailyLimit() - completed),
                prizes.stream().map(FreeDrawService::view).toList(),
                clock.instant());
    }

    @Transactional
    public SessionResponse openSession(UUID userId) {
        FreeDrawActivityEntity activity = activity();
        requireRemaining(userId, activity);
        UUID id = idSupplier.get();
        FreeDrawSessionEntity session =
                FreeDrawSessionEntity.open(
                        id,
                        userId,
                        activity.getId(),
                        drawDate(),
                        clock.instant(),
                        clock.instant().plusSeconds(SESSION_TTL_SECONDS));
        sessionRepository.save(session);
        return new SessionResponse(
                id.toString(), id.toString(), activity.getAdPlacementId(), session.getExpiresAt());
    }

    @Transactional
    public RewardResponse claim(
            UUID userId,
            UUID sessionId,
            String placementId,
            String adSourceId,
            String showId) {
        FreeDrawSessionEntity session = lockedSession(userId, sessionId);
        FreeDrawActivityEntity activity = activity();
        requireSessionActivity(session, activity, placementId, adSourceId);
        if (FreeDrawSessionEntity.STATUS_GRANTED.equals(session.getStatus())) {
            return response(session, true, completed(userId, activity), wallet(userId));
        }
        if (clock.instant().isAfter(session.getExpiresAt())) {
            throw invalidSession("广告奖励会话已过期，请重新观看");
        }
        PlayerWalletEntity wallet = lockedWallet(userId);
        requireRemaining(userId, activity);
        FreeDrawPrizeEntity prize = pick(prizes(activity));
        credit(wallet, prize);
        walletRepository.save(wallet);
        session.grant(prize, adSourceId, showId, clock.instant());
        sessionRepository.save(session);
        return response(session, false, completed(userId, activity) + 1, wallet);
    }

    private RewardResponse response(
            FreeDrawSessionEntity session,
            boolean replayed,
            int completed,
            PlayerWalletEntity wallet) {
        FreeDrawActivityEntity activity = activity();
        return new RewardResponse(
                session.getId().toString(),
                replayed,
                new PrizeView(
                        session.getRewardPrizeId().toString(),
                        session.getRewardType(),
                        session.getRewardAmount(),
                        session.getRewardName(),
                        session.getRewardIconKey()),
                Math.max(0, activity.getDailyLimit() - completed),
                ShopWalletResponse.from(wallet));
    }

    private FreeDrawSessionEntity lockedSession(UUID userId, UUID sessionId) {
        FreeDrawSessionEntity session =
                sessionRepository
                        .findLockedById(sessionId)
                        .orElseThrow(() -> invalidSession("广告奖励会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw invalidSession("广告奖励会话不属于当前账号");
        }
        return session;
    }

    private void requireSessionActivity(
            FreeDrawSessionEntity session,
            FreeDrawActivityEntity activity,
            String placementId,
            String adSourceId) {
        if (!session.getActivityId().equals(activity.getId())
                || !session.getDrawDate().equals(drawDate())
                || !Objects.equals(activity.getAdPlacementId(), placementId)
                || !ORIGINAL_REWARDED_SOURCES.contains(activity.getProviderSourceId())
                || !ORIGINAL_REWARDED_SOURCES.contains(adSourceId)) {
            throw invalidSession("广告奖励凭证与当前活动不匹配");
        }
    }

    private void requireRemaining(UUID userId, FreeDrawActivityEntity activity) {
        if (completed(userId, activity) >= activity.getDailyLimit()) {
            throw new ApiException(ErrorCode.FREE_DRAW_LIMIT_REACHED, "今日免费抽奖次数已用完");
        }
    }

    private int completed(UUID userId, FreeDrawActivityEntity activity) {
        return Math.toIntExact(
                sessionRepository.countByUserIdAndActivityIdAndDrawDateAndStatus(
                        userId,
                        activity.getId(),
                        drawDate(),
                        FreeDrawSessionEntity.STATUS_GRANTED));
    }

    private FreeDrawActivityEntity activity() {
        return activityRepository
                .findFirstByEnabledTrueOrderByActivityCodeAsc()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.FREE_DRAW_NOT_AVAILABLE, "免费抽奖活动未开启"));
    }

    private List<FreeDrawPrizeEntity> prizes(FreeDrawActivityEntity activity) {
        List<FreeDrawPrizeEntity> prizes =
                prizeRepository.findByActivityIdAndEnabledTrueOrderByDisplayOrderAsc(
                        activity.getId());
        if (prizes.isEmpty()) {
            throw new ApiException(ErrorCode.FREE_DRAW_NOT_AVAILABLE, "免费抽奖奖池未配置");
        }
        return prizes;
    }

    private FreeDrawPrizeEntity pick(List<FreeDrawPrizeEntity> prizes) {
        int total = prizes.stream().mapToInt(FreeDrawPrizeEntity::getWeight).sum();
        int roll = randomBound.applyAsInt(total);
        for (FreeDrawPrizeEntity prize : prizes) {
            roll -= prize.getWeight();
            if (roll < 0) return prize;
        }
        throw new IllegalStateException("Free draw prize weights are invalid");
    }

    private PlayerWalletEntity lockedWallet(UUID userId) {
        return walletRepository
                .findLockedByUserId(userId)
                .orElseGet(() -> walletRepository.save(new PlayerWalletEntity(userId, 0, 0, 0, 0)));
    }

    private PlayerWalletEntity wallet(UUID userId) {
        return walletRepository
                .findById(userId)
                .orElseGet(() -> new PlayerWalletEntity(userId, 0, 0, 0, 0));
    }

    private static void credit(PlayerWalletEntity wallet, FreeDrawPrizeEntity prize) {
        switch (prize.getRewardType()) {
            case "COIN" -> wallet.addCoins(prize.getRewardAmount());
            case "DIAMOND" -> wallet.addDiamonds(prize.getRewardAmount());
            default -> throw new IllegalStateException("Unsupported free draw reward type");
        }
    }

    private LocalDate drawDate() {
        return clock.instant().atZone(ACTIVITY_ZONE).toLocalDate();
    }

    private static PrizeView view(FreeDrawPrizeEntity prize) {
        return new PrizeView(
                prize.getId().toString(),
                prize.getRewardType(),
                prize.getRewardAmount(),
                prize.getDisplayName(),
                prize.getIconKey());
    }

    private static ApiException invalidSession(String message) {
        return new ApiException(ErrorCode.FREE_DRAW_SESSION_INVALID, message);
    }
}
