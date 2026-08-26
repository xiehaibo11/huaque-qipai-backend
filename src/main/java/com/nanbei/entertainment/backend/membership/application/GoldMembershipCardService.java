package com.nanbei.entertainment.backend.membership.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.membership.domain.GoldMembershipCardClaimEntity;
import com.nanbei.entertainment.backend.membership.domain.GoldMembershipCardEntity;
import com.nanbei.entertainment.backend.membership.infrastructure.GoldMembershipCardClaimRepository;
import com.nanbei.entertainment.backend.membership.infrastructure.GoldMembershipCardRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoldMembershipCardService {
    private static final ZoneId CHINA_TIME = ZoneId.of("Asia/Shanghai");
    private static final String WEEK = "GOLD_MEMBER_WEEK";
    private static final String MONTH = "GOLD_MEMBER_MONTH";
    private static final List<Plan> PLANS =
            List.of(
                    new Plan(WEEK, "金币周卡", 7, 10_000),
                    new Plan(MONTH, "金币月卡", 30, 15_000));

    private final GoldMembershipCardRepository cardRepository;
    private final GoldMembershipCardClaimRepository claimRepository;
    private final PlayerWalletRepository walletRepository;
    private final Clock clock;

    @Autowired
    public GoldMembershipCardService(
            GoldMembershipCardRepository cardRepository,
            GoldMembershipCardClaimRepository claimRepository,
            PlayerWalletRepository walletRepository) {
        this(cardRepository, claimRepository, walletRepository, Clock.systemUTC());
    }

    GoldMembershipCardService(
            GoldMembershipCardRepository cardRepository,
            GoldMembershipCardClaimRepository claimRepository,
            PlayerWalletRepository walletRepository,
            Clock clock) {
        this.cardRepository = cardRepository;
        this.claimRepository = claimRepository;
        this.walletRepository = walletRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GoldMembershipCardsResponse cards(UUID userId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(CHINA_TIME));
        Map<String, GoldMembershipCardEntity> cards =
                cardRepository.findAllByUserId(userId).stream()
                        .collect(
                                Collectors.toMap(
                                        GoldMembershipCardEntity::getProductCode,
                                        Function.identity()));
        Set<String> claimed =
                claimRepository.findAllByUserIdAndClaimedOn(userId, today).stream()
                        .map(GoldMembershipCardClaimEntity::getProductCode)
                        .collect(Collectors.toSet());
        return new GoldMembershipCardsResponse(
                PLANS.stream()
                        .map(
                                plan ->
                                        status(
                                                plan,
                                                cards.get(plan.productCode()),
                                                claimed.contains(plan.productCode()),
                                                now))
                        .toList());
    }

    @Transactional
    public void activate(UUID userId, String purchasedProductCode, long durationDays) {
        Plan plan = plan(purchasedProductCode);
        if (durationDays != plan.durationDays()) {
            throw new IllegalStateException("gold membership duration mismatch");
        }
        cardRepository.acquireCardLock(userId + ":" + plan.productCode());
        GoldMembershipCardEntity card =
                cardRepository
                        .findLocked(userId, plan.productCode())
                        .orElseGet(
                                () ->
                                        new GoldMembershipCardEntity(
                                                userId, plan.productCode()));
        card.activate(Duration.ofDays(durationDays), clock.instant());
        cardRepository.save(card);
    }

    @Transactional
    public GoldMembershipCardStatus claim(UUID userId, String productCode) {
        Plan plan = plan(productCode);
        PlayerWalletEntity wallet =
                walletRepository
                        .findLockedByUserId(userId)
                        .orElseGet(
                                () ->
                                        walletRepository.save(
                                                new PlayerWalletEntity(
                                                        userId, 0, 0, 0, 0)));
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(CHINA_TIME));
        String claimKey = userId + ":" + productCode + ":" + today;
        claimRepository.acquireClaimLock(claimKey);
        GoldMembershipCardEntity card =
                cardRepository
                        .findLocked(userId, productCode)
                        .filter(candidate -> candidate.isActiveAt(now))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GOLD_MEMBERSHIP_NOT_ACTIVE,
                                                "金币会员卡未开通或已过期"));
        if (claimRepository.existsByUserIdAndProductCodeAndClaimedOn(
                userId, productCode, today)) {
            throw new ApiException(
                    ErrorCode.GOLD_MEMBERSHIP_ALREADY_CLAIMED,
                    "今日金币已领取");
        }
        wallet.addCoins(plan.dailyCoins());
        claimRepository.save(
                new GoldMembershipCardClaimEntity(
                        userId, productCode, today, plan.dailyCoins()));
        walletRepository.save(wallet);
        return status(plan, card, true, now);
    }

    private static GoldMembershipCardStatus status(
            Plan plan,
            GoldMembershipCardEntity card,
            boolean claimed,
            Instant now) {
        boolean active = card != null && card.isActiveAt(now);
        String state = !active ? "NOT_ACTIVE" : claimed ? "HAS_AWARD" : "NOT_AWARD";
        long remaining =
                active
                        ? Math.max(0, Duration.between(now, card.getExpiresAt()).getSeconds())
                        : 0;
        return new GoldMembershipCardStatus(
                plan.productCode(),
                plan.title(),
                plan.durationDays(),
                plan.dailyCoins(),
                state,
                remaining);
    }

    private static Plan plan(String productCode) {
        return PLANS.stream()
                .filter(candidate -> candidate.productCode().equals(productCode))
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.GOLD_MEMBERSHIP_CARD_NOT_FOUND,
                                        "金币会员卡不存在"));
    }

    public static boolean supports(String productCode) {
        return PLANS.stream()
                .anyMatch(plan -> plan.productCode().equals(productCode));
    }

    private record Plan(
            String productCode, String title, int durationDays, long dailyCoins) {}
}
