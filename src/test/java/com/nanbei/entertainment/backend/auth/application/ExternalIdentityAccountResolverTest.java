package com.nanbei.entertainment.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalIdentityAccountResolverTest {
    @Mock UserRepository userRepository;
    @Mock UserIdentityRepository identityRepository;

    @Test
    void supplementsOpenIdAliasWithoutCreatingASecondUser() {
        UserEntity user = UserEntity.create("微信用户");
        UserIdentityEntity unionIdentity =
                new UserIdentityEntity(
                        user,
                        IdentityProvider.WECHAT,
                        "unionid:union-1",
                        null);
        when(identityRepository.findAllByProviderAndProviderSubjectIn(
                        IdentityProvider.WECHAT,
                        List.of(
                                "appid:wx-test:openid:open-1",
                                "unionid:union-1")))
                .thenReturn(List.of(unionIdentity));

        UserEntity resolved =
                new ExternalIdentityAccountResolver(
                                userRepository, identityRepository)
                        .resolve(
                                new ExternalIdentity(
                                        IdentityProvider.WECHAT,
                                        "unionid:union-1",
                                        List.of("appid:wx-test:openid:open-1"),
                                        null,
                                        null,
                                        null),
                                "微信用户");

        assertThat(resolved).isSameAs(user);
        ArgumentCaptor<UserIdentityEntity> added =
                ArgumentCaptor.forClass(UserIdentityEntity.class);
        verify(identityRepository).save(added.capture());
        assertThat(added.getValue().getProviderSubject())
                .isEqualTo("appid:wx-test:openid:open-1");
    }
}
