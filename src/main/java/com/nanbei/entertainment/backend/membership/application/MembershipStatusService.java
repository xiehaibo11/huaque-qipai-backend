package com.nanbei.entertainment.backend.membership.application;

import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.membership.domain.UserMembershipEntity;
import com.nanbei.entertainment.backend.membership.infrastructure.UserMembershipRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipStatusService {
    private final UserMembershipRepository membershipRepository;
    private final PlayerProfileService profileService;
    private final PlayerProfileRepository profileRepository;
    private final Clock clock;

    @Autowired
    public MembershipStatusService(
            UserMembershipRepository membershipRepository,
            PlayerProfileService profileService,
            PlayerProfileRepository profileRepository) {
        this(membershipRepository, profileService, profileRepository, Clock.systemUTC());
    }

    MembershipStatusService(
            UserMembershipRepository membershipRepository,
            PlayerProfileService profileService,
            PlayerProfileRepository profileRepository,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.profileService = profileService;
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    @Transactional
    public MembershipStatus status(UUID userId) {
        profileService.ensureProfile(userId);
        Instant now = clock.instant();
        UserMembershipEntity membership =
                membershipRepository.findById(userId).orElse(null);
        boolean active = membership != null && membership.isActiveAt(now);
        synchronizeProfileMembershipLevel(userId, active ? membership.getMembershipLevel() : 0);
        if (membership == null) {
            return new MembershipStatus(false, 0, null, null, false, 0);
        }
        return new MembershipStatus(
                active,
                active ? membership.getMembershipLevel() : 0,
                membership.getStartedAt(),
                membership.getExpiresAt(),
                membership.isAutoRenew(),
                active
                        ? Math.max(0, ChronoUnit.DAYS.between(now, membership.getExpiresAt()))
                        : 0);
    }

    @Transactional
    public void activate(
            UUID userId,
            int durationDays,
            boolean autoRenew,
            UUID orderId,
            Instant paidAt) {
        UserMembershipEntity membership =
                membershipRepository.findById(userId)
                        .orElseGet(() -> new UserMembershipEntity(userId));
        membership.activate(
                1,
                Duration.ofDays(durationDays),
                autoRenew,
                orderId,
                paidAt == null ? clock.instant() : paidAt);
        membershipRepository.save(membership);
        synchronizeProfileMembershipLevel(userId, 1);
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID userId) {
        Instant now = clock.instant();
        return membershipRepository.findById(userId)
                .map(membership -> membership.isActiveAt(now))
                .orElse(false);
    }

    private void synchronizeProfileMembershipLevel(UUID userId, int membershipLevel) {
        PlayerProfileEntity profile = profileService.ensureProfile(userId);
        if (profile.getMembershipLevel() == membershipLevel) {
            return;
        }
        profile.setMembershipLevel(membershipLevel);
        profileRepository.save(profile);
    }
}
