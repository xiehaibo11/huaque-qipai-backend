package com.nanbei.entertainment.backend.wechatpush.application;

import com.nanbei.entertainment.backend.auth.infrastructure.RefreshTokenRepository;
import com.nanbei.entertainment.backend.avatar.application.AvatarService;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import com.nanbei.entertainment.backend.wechatpush.domain.WeChatPushEventEntity;
import com.nanbei.entertainment.backend.wechatpush.infrastructure.WeChatPushEventRepository;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionGrantInvalidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeChatAuthorizationEventService {
    private static final String PROFILE_MODIFIED = "user_info_modified";
    private static final String AUTHORIZATION_REVOKED = "user_authorization_revoke";
    private static final String AUTHORIZATION_CANCELLED = "user_authorization_cancellation";
    private static final String FULL_MOBILE_AUTHORIZATION = "301";

    private final UserIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshRepository;
    private final AvatarService avatarService;
    private final CryptoService cryptoService;
    private final WeChatPushEventRepository eventRepository;
    private final WeChatSubscriptionGrantInvalidator subscriptionGrantInvalidator;

    public WeChatAuthorizationEventService(
            UserIdentityRepository identityRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AvatarService avatarService,
            CryptoService cryptoService,
            WeChatPushEventRepository eventRepository,
            WeChatSubscriptionGrantInvalidator subscriptionGrantInvalidator) {
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
        this.refreshRepository = refreshRepository;
        this.avatarService = avatarService;
        this.cryptoService = cryptoService;
        this.eventRepository = eventRepository;
        this.subscriptionGrantInvalidator = subscriptionGrantInvalidator;
    }

    @Transactional
    public void handle(WeChatAuthorizationEvent event) {
        String fingerprint = cryptoService.sha256(event.fingerprintMaterial());
        eventRepository.acquireEventLock("wechat-push:" + fingerprint);
        if (eventRepository.existsByFingerprint(fingerprint)) {
            return;
        }

        identitySubjects(event)
                .forEach(
                        subject ->
                                identityRepository.acquireIdentityLock(
                                        IdentityProvider.WECHAT + ":" + subject));
        Optional<UserIdentityEntity> target = findIdentity(event);
        if (target.isPresent() && isKnownAuthorizationEvent(event.eventType())) {
            UserEntity user = target.get().getUser();
            clearWechatProfile(user);
            if (requiresFullRevocation(event)) {
                revokeWechatAuthorization(user);
            }
        }
        eventRepository.save(
                new WeChatPushEventEntity(fingerprint, event.eventType()));
    }

    private Optional<UserIdentityEntity> findIdentity(WeChatAuthorizationEvent event) {
        if (event.unionId() != null) {
            Optional<UserIdentityEntity> byUnion =
                    identityRepository.findByProviderAndProviderSubject(
                            IdentityProvider.WECHAT, "unionid:" + event.unionId());
            if (byUnion.isPresent()) {
                return byUnion;
            }
        }
        if (event.openId() == null) {
            return Optional.empty();
        }
        return identityRepository.findByProviderAndProviderSubject(
                IdentityProvider.WECHAT,
                "appid:" + event.appId() + ":openid:" + event.openId());
    }

    private static List<String> identitySubjects(WeChatAuthorizationEvent event) {
        List<String> subjects = new ArrayList<>();
        if (event.unionId() != null) {
            subjects.add("unionid:" + event.unionId());
        }
        if (event.openId() != null) {
            subjects.add(
                    "appid:" + event.appId() + ":openid:" + event.openId());
        }
        return subjects.stream().distinct().sorted().toList();
    }

    private void clearWechatProfile(UserEntity user) {
        user.clearWechatDisplayName();
        userRepository.save(user);
        avatarService.clearWechatAvatar(user.getId());
    }

    private void revokeWechatAuthorization(UserEntity user) {
        subscriptionGrantInvalidator.invalidate(user.getId());
        List<UserIdentityEntity> identities =
                identityRepository.findByUser_IdOrderByCreatedAtAsc(user.getId());
        List<UserIdentityEntity> wechatIdentities =
                identities.stream()
                        .filter(item -> item.getProvider() == IdentityProvider.WECHAT)
                        .toList();
        identityRepository.deleteAll(wechatIdentities);
        if (identities.size() == wechatIdentities.size()) {
            user.deactivate();
        }
        user.invalidateSessions();
        userRepository.save(user);
        refreshRepository.acquireUserSessionLock("auth-session:" + user.getId());
        refreshRepository.revokeAllByUserId(user.getId(), Instant.now());
    }

    private static boolean requiresFullRevocation(WeChatAuthorizationEvent event) {
        return AUTHORIZATION_CANCELLED.equals(event.eventType())
                || (AUTHORIZATION_REVOKED.equals(event.eventType())
                        && FULL_MOBILE_AUTHORIZATION.equals(event.revokeInfo()));
    }

    private static boolean isKnownAuthorizationEvent(String eventType) {
        return PROFILE_MODIFIED.equals(eventType)
                || AUTHORIZATION_REVOKED.equals(eventType)
                || AUTHORIZATION_CANCELLED.equals(eventType);
    }
}
