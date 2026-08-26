package com.nanbei.entertainment.backend.wechatpush.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.auth.infrastructure.RefreshTokenRepository;
import com.nanbei.entertainment.backend.avatar.application.AvatarService;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.profile.ProfileSource;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import com.nanbei.entertainment.backend.wechatpush.infrastructure.WeChatPushEventRepository;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionGrantInvalidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeChatAuthorizationEventServiceTest {
    @Mock UserIdentityRepository identityRepository;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshRepository;
    @Mock AvatarService avatarService;
    @Mock CryptoService cryptoService;
    @Mock WeChatPushEventRepository eventRepository;
    @Mock WeChatSubscriptionGrantInvalidator subscriptionGrantInvalidator;

    private WeChatAuthorizationEventService service;
    private UserEntity user;
    private UserIdentityEntity wechatIdentity;

    @BeforeEach
    void setUp() {
        service =
                new WeChatAuthorizationEventService(
                        identityRepository,
                        userRepository,
                        refreshRepository,
                        avatarService,
                        cryptoService,
                        eventRepository,
                        subscriptionGrantInvalidator);
        user = UserEntity.create("微信昵称", ProfileSource.WECHAT);
        wechatIdentity =
                new UserIdentityEntity(
                        user,
                        IdentityProvider.WECHAT,
                        "appid:wx-test:openid:openid-1",
                        null);
        when(cryptoService.sha256(any())).thenReturn("a".repeat(64));
        when(eventRepository.existsByFingerprint(any())).thenReturn(false);
        when(identityRepository.findByProviderAndProviderSubject(
                        IdentityProvider.WECHAT,
                        "appid:wx-test:openid:openid-1"))
                .thenReturn(Optional.of(wechatIdentity));
    }

    @Test
    void profileChangeClearsOnlyWechatSourcedProfile() {
        service.handle(event("user_info_modified", null));

        assertThat(user.getDisplayName()).isEqualTo("微信用户");
        verify(identityRepository)
                .acquireIdentityLock(
                        "WECHAT:appid:wx-test:openid:openid-1");
        verify(avatarService).clearWechatAvatar(user.getId());
        verify(identityRepository, never()).deleteAll(any());
        verify(refreshRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void fullRevokeRemovesWechatAliasesAndDisablesWechatOnlyUser() {
        when(identityRepository.findByUser_IdOrderByCreatedAtAsc(user.getId()))
                .thenReturn(List.of(wechatIdentity));

        service.handle(event("user_authorization_revoke", "301"));

        verify(identityRepository).deleteAll(List.of(wechatIdentity));
        verify(subscriptionGrantInvalidator).invalidate(user.getId());
        verify(refreshRepository).revokeAllByUserId(eq(user.getId()), any());
        assertThat(user.isActive()).isFalse();
        assertThat(user.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    void partialRevokeDoesNotGuessThatAllLoginAuthorizationWasRemoved() {
        service.handle(event("user_authorization_revoke", "1"));

        verify(identityRepository, never()).deleteAll(any());
        verify(refreshRepository, never()).revokeAllByUserId(any(), any());
        assertThat(user.isActive()).isTrue();
    }

    private static WeChatAuthorizationEvent event(String type, String revokeInfo) {
        return new WeChatAuthorizationEvent(
                type,
                1_627_359_464L,
                "wx-test",
                "openid-1",
                null,
                revokeInfo);
    }
}
