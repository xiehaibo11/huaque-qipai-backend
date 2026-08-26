package com.nanbei.entertainment.backend.personalcenter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.gamehome.application.GameHomeService;
import com.nanbei.entertainment.backend.gamehome.application.GameHomeSnapshot;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.membership.application.MembershipStatus;
import com.nanbei.entertainment.backend.membership.application.MembershipStatusService;
import com.nanbei.entertainment.backend.realname.application.RealNameService;
import com.nanbei.entertainment.backend.realname.application.RealNameStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalCenterServiceTest {
    @Mock GameHomeService gameHomeService;
    @Mock UserIdentityRepository identityRepository;
    @Mock PersonalCenterFunctionService functionService;
    @Mock RealNameService realNameService;
    @Mock MembershipStatusService membershipStatusService;

    PersonalCenterService service;

    @BeforeEach
    void setUp() {
        service =
                new PersonalCenterService(
                        gameHomeService,
                        identityRepository,
                        functionService,
                        realNameService,
                        membershipStatusService);
    }

    @Test
    void aggregatesAuthoritativePlayerWalletRegionAndMaskedIdentityData() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.create("WhimSeeker");
        GameHomeSnapshot home =
                new GameHomeSnapshot(
                        new GameHomeSnapshot.Player(
                                userId,
                                1084375590L,
                                "WhimSeeker",
                                "avatar-user",
                                1),
                        new GameHomeSnapshot.Wallet(9L, 2L, 1835L, 0L),
                        new GameHomeSnapshot.Region(900021L, "台州"),
                        List.of());
        UserIdentityEntity phone =
                new UserIdentityEntity(
                        user,
                        IdentityProvider.PHONE,
                        "phone:15812656092",
                        "15812656092");
        UserIdentityEntity wechat =
                new UserIdentityEntity(
                        user,
                        IdentityProvider.WECHAT,
                        "unionid:test",
                        null);

        when(gameHomeService.load(userId)).thenReturn(home);
        when(identityRepository.findByUser_IdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(phone, wechat));
        when(functionService.loadPrivacy(userId))
                .thenReturn(
                        new PersonalCenterPrivacySettings(
                                true, true, true, true, false, true));
        Instant expiresAt = Instant.parse("2026-09-30T12:00:00Z");
        when(realNameService.status(userId))
                .thenReturn(
                        new RealNameStatus(
                                "VERIFIED",
                                "张*",
                                "330***********1234",
                                Instant.parse("2026-01-02T03:04:05Z"),
                                true));
        when(membershipStatusService.snapshot(userId))
                .thenReturn(
                        new MembershipStatus(
                                true,
                                1,
                                Instant.parse("2026-08-01T12:00:00Z"),
                                expiresAt,
                                false,
                                38L));

        PersonalCenterSnapshot result = service.load(userId);

        assertThat(result.player().publicPlayerId()).isEqualTo(1084375590L);
        assertThat(result.player().displayName()).isEqualTo("WhimSeeker");
        assertThat(result.wallet().purchasedRoomCards()).isEqualTo(9L);
        assertThat(result.wallet().boundRoomCards()).isEqualTo(2L);
        assertThat(result.wallet().coins()).isEqualTo(1835L);
        assertThat(result.wallet().diamonds()).isZero();
        assertThat(result.account().phoneBound()).isTrue();
        assertThat(result.account().maskedPhone()).isEqualTo("158****6092");
        assertThat(result.account().identityProviders())
                .containsExactly("PHONE", "WECHAT");
        assertThat(result.region().areaName()).isEqualTo("台州");
        assertThat(result.healthCertification().status())
                .isEqualTo("VERIFIED");
        assertThat(result.healthCertification().realNameMasked())
                .isEqualTo("张*");
        assertThat(result.membership().active()).isTrue();
        assertThat(result.membership().expiresAt()).isEqualTo(expiresAt);
        assertThat(result.membership().remainingDays()).isEqualTo(38L);
        assertThat(result.capabilities().avatarRefresh()).isTrue();
        assertThat(result.capabilities().accountDeletion()).isTrue();
        assertThat(result.capabilities().phoneRebind()).isTrue();
        assertThat(result.privacy().allowFriendRequests()).isTrue();
        assertThat(result.privacy().personalizedRecommendations()).isFalse();
    }

    @Test
    void reportsAnUnboundPhoneWithoutLeakingAnIdentitySubject() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.create("微信用户");
        GameHomeSnapshot home =
                new GameHomeSnapshot(
                        new GameHomeSnapshot.Player(
                                userId, 1084375591L, "微信用户", "avatar-default", 0),
                        new GameHomeSnapshot.Wallet(0L, 0L, 0L, 0L),
                        new GameHomeSnapshot.Region(900021L, "台州"),
                        List.of());
        UserIdentityEntity wechat =
                new UserIdentityEntity(
                        user,
                        IdentityProvider.WECHAT,
                        "unionid:must-not-leak",
                        null);

        when(gameHomeService.load(userId)).thenReturn(home);
        when(identityRepository.findByUser_IdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(wechat));
        when(functionService.loadPrivacy(userId))
                .thenReturn(
                        new PersonalCenterPrivacySettings(
                                true, true, true, true, false, true));
        when(realNameService.status(userId))
                .thenReturn(RealNameStatus.unverified(false));
        when(membershipStatusService.snapshot(userId))
                .thenReturn(
                        new MembershipStatus(
                                false, 0, null, null, false, 0L));

        PersonalCenterSnapshot result = service.load(userId);

        assertThat(result.account().phoneBound()).isFalse();
        assertThat(result.account().maskedPhone()).isEmpty();
        assertThat(result.account().identityProviders())
                .containsExactly("WECHAT");
        assertThat(result.toString()).doesNotContain("must-not-leak");
    }
}
