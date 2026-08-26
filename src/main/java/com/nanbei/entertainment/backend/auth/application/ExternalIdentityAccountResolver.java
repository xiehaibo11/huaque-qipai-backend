package com.nanbei.entertainment.backend.auth.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.common.profile.ProfileSource;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExternalIdentityAccountResolver {
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;

    public ExternalIdentityAccountResolver(
            UserRepository userRepository,
            UserIdentityRepository identityRepository) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
    }

    public UserEntity resolve(ExternalIdentity identity, String defaultDisplayName) {
        List<String> subjects =
                identity.subjects().stream()
                        .filter(subject -> subject != null && !subject.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
        if (subjects.isEmpty()) {
            throw new ApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIAL, "第三方登录身份无效");
        }
        subjects.forEach(
                subject ->
                        identityRepository.acquireIdentityLock(
                                identity.provider() + ":" + subject));

        List<UserIdentityEntity> existing =
                identityRepository.findAllByProviderAndProviderSubjectIn(
                        identity.provider(), subjects);
        Set<UUID> userIds = new HashSet<>();
        existing.forEach(item -> userIds.add(item.getUser().getId()));
        if (userIds.size() > 1) {
            throw new ApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIAL,
                    "第三方登录身份存在账号冲突，请联系客服处理");
        }

        UserEntity user =
                existing.isEmpty()
                        ? userRepository.save(
                                UserEntity.create(
                                        defaultDisplayName,
                                        identity.provider() == IdentityProvider.WECHAT
                                                ? ProfileSource.WECHAT
                                                : ProfileSource.SYSTEM))
                        : existing.getFirst().getUser();
        Set<String> existingSubjects = new HashSet<>();
        existing.forEach(item -> existingSubjects.add(item.getProviderSubject()));
        subjects.stream()
                .filter(subject -> !existingSubjects.contains(subject))
                .forEach(
                        subject ->
                                identityRepository.save(
                                        new UserIdentityEntity(
                                                user,
                                                identity.provider(),
                                                subject,
                                                null)));
        return user;
    }

    public UserEntity resolveSimple(
            IdentityProvider provider,
            String subject,
            String phoneNumber,
            String displayName) {
        identityRepository.acquireIdentityLock(provider + ":" + subject);
        return identityRepository
                .findByProviderAndProviderSubject(provider, subject)
                .map(UserIdentityEntity::getUser)
                .orElseGet(
                        () -> {
                            UserEntity user =
                                    userRepository.save(UserEntity.create(displayName));
                            identityRepository.save(
                                    new UserIdentityEntity(
                                            user,
                                            provider,
                                            subject,
                                            phoneNumber));
                            return user;
                        });
    }
}
