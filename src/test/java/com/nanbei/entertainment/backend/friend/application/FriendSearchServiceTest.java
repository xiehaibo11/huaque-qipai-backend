package com.nanbei.entertainment.backend.friend.application;

import static com.nanbei.entertainment.backend.friend.application.FriendServiceTest.profileOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.friend.domain.FriendApplicationStatus;
import com.nanbei.entertainment.backend.friend.domain.FriendRelation;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendApplicationRepository;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendshipRepository;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendSearchServiceTest {
    @Mock FriendshipRepository friendshipRepository;
    @Mock FriendApplicationRepository applicationRepository;
    @Mock PlayerProfileRepository profileRepository;
    @Mock PlayerProfileService profileService;
    @Mock UserRepository userRepository;
    @Mock UserIdentityRepository identityRepository;

    FriendSearchService service;

    @BeforeEach
    void setUp() {
        service =
                new FriendSearchService(
                        friendshipRepository,
                        applicationRepository,
                        profileRepository,
                        profileService,
                        userRepository,
                        identityRepository);
    }

    @Test
    void searchesByPublicPlayerId() {
        UUID searcherId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0016");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(userRepository.findById(target.getId()))
                .thenReturn(Optional.of(target));

        FriendSearchResult result =
                service.search(searcherId, "1084375590");

        assertThat(result.publicPlayerId()).isEqualTo(1084375590L);
        assertThat(result.displayName()).isEqualTo("手机用户0016");
        assertThat(result.avatarKey()).isEqualTo("avatar_default");
        assertThat(result.relation()).isEqualTo(FriendRelation.NONE);
    }

    @Test
    void searchesByPhoneNumberAcrossPhoneAndOneTapIdentities() {
        UUID searcherId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0017");
        UserIdentityEntity identity =
                new UserIdentityEntity(
                        target,
                        IdentityProvider.ONE_TAP,
                        "phone:13800138000",
                        "13800138000");
        when(identityRepository.findByProviderAndProviderSubject(
                        IdentityProvider.PHONE, "13800138000"))
                .thenReturn(Optional.empty());
        when(identityRepository.findByProviderAndProviderSubject(
                        IdentityProvider.ONE_TAP, "phone:13800138000"))
                .thenReturn(Optional.of(identity));
        PlayerProfileEntity profile = profileOf(target, 1084375590L);
        when(profileService.ensureProfile(target.getId()))
                .thenReturn(profile);

        FriendSearchResult result =
                service.search(searcherId, "13800138000");

        assertThat(result.publicPlayerId()).isEqualTo(1084375590L);
        assertThat(result.displayName()).isEqualTo("手机用户0017");
    }

    @Test
    void searchReportsExistingFriendship() {
        UserEntity target = UserEntity.create("手机用户0018");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(userRepository.findById(target.getId()))
                .thenReturn(Optional.of(target));
        UUID searcherId = UUID.randomUUID();
        when(friendshipRepository.existsByUserIdAndFriendId(
                        searcherId, target.getId()))
                .thenReturn(true);

        FriendSearchResult result =
                service.search(searcherId, "1084375590");

        assertThat(result.relation()).isEqualTo(FriendRelation.FRIEND);
    }

    @Test
    void rejectsSearchingSelfByPhoneNumber() {
        UserEntity self = UserEntity.create("手机用户0019");
        UserIdentityEntity identity =
                new UserIdentityEntity(
                        self,
                        IdentityProvider.PHONE,
                        "13800138000",
                        "13800138000");
        when(identityRepository.findByProviderAndProviderSubject(
                        IdentityProvider.PHONE, "13800138000"))
                .thenReturn(Optional.of(identity));

        assertThatThrownBy(
                        () -> service.search(self.getId(), "13800138000"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_SELF_OPERATION);
    }

    @Test
    void searchReportsRejectedOutgoingApplication() {
        UserEntity target = UserEntity.create("手机用户0026");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(userRepository.findById(target.getId()))
                .thenReturn(Optional.of(target));
        UUID searcherId = UUID.randomUUID();
        when(applicationRepository.existsByRequesterIdAndTargetIdAndStatus(
                        searcherId,
                        target.getId(),
                        FriendApplicationStatus.PENDING))
                .thenReturn(false);
        when(applicationRepository.existsByRequesterIdAndTargetIdAndStatus(
                        target.getId(),
                        searcherId,
                        FriendApplicationStatus.PENDING))
                .thenReturn(false);
        when(applicationRepository.existsByRequesterIdAndTargetIdAndStatus(
                        searcherId,
                        target.getId(),
                        FriendApplicationStatus.REJECTED))
                .thenReturn(true);

        FriendSearchResult result =
                service.search(searcherId, "1084375590");

        assertThat(result.relation()).isEqualTo(FriendRelation.REJECTED);
    }

    @Test
    void searchPrefersPendingOverRejected() {
        UserEntity target = UserEntity.create("手机用户0027");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(userRepository.findById(target.getId()))
                .thenReturn(Optional.of(target));
        UUID searcherId = UUID.randomUUID();
        when(applicationRepository.existsByRequesterIdAndTargetIdAndStatus(
                        searcherId,
                        target.getId(),
                        FriendApplicationStatus.PENDING))
                .thenReturn(true);

        FriendSearchResult result =
                service.search(searcherId, "1084375590");

        assertThat(result.relation()).isEqualTo(FriendRelation.PENDING);
    }

    @Test
    void searchIgnoresRejectedIncomingApplication() {
        UserEntity target = UserEntity.create("手机用户0028");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(userRepository.findById(target.getId()))
                .thenReturn(Optional.of(target));
        UUID searcherId = UUID.randomUUID();

        FriendSearchResult result =
                service.search(searcherId, "1084375590");

        assertThat(result.relation()).isEqualTo(FriendRelation.NONE);
    }

    @Test
    void rejectsSearchWithoutMatch() {
        UUID searcherId = UUID.randomUUID();
        when(profileRepository.findByPublicPlayerId(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(searcherId, "1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_NOT_FOUND);
        assertThatThrownBy(() -> service.search(searcherId, "abc"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_NOT_FOUND);
    }
}
