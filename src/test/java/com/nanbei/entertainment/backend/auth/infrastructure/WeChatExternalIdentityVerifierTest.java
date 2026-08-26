package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.auth.application.ExternalIdentity;
import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WeChatExternalIdentityVerifierTest {
    @Test
    void prefersUnionId() {
        WeChatCodeExchange exchange =
                code ->
                        new WeChatTokenResponse(
                                "openid-1", "unionid-1", null, null);
        WeChatExternalIdentityVerifier verifier =
                new WeChatExternalIdentityVerifier(enabledProperties(), exchange);

        ExternalIdentity identity =
                verifier.verify(IdentityProvider.WECHAT, "one-time-code");

        assertThat(identity.provider()).isEqualTo(IdentityProvider.WECHAT);
        assertThat(identity.subject()).isEqualTo("unionid:unionid-1");
        assertThat(identity.subjects())
                .containsExactly(
                        "unionid:unionid-1",
                        "appid:wx-test:openid:openid-1");
    }

    @Test
    void carriesWechatProfileIntoVerifiedIdentity() {
        WeChatCodeExchange exchange =
                code ->
                        new WeChatTokenResponse(
                                "openid-1",
                                "unionid-1",
                                null,
                                null,
                                "wechat-access-token",
                                "牌友昵称",
                                new byte[] {1, 2, 3},
                                "image/jpeg");
        WeChatExternalIdentityVerifier verifier =
                new WeChatExternalIdentityVerifier(enabledProperties(), exchange);

        ExternalIdentity identity =
                verifier.verify(IdentityProvider.WECHAT, "one-time-code");

        assertThat(identity.displayName()).isEqualTo("牌友昵称");
        assertThat(identity.avatarBytes()).containsExactly(1, 2, 3);
        assertThat(identity.avatarContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void namespacesOpenIdWhenUnionIdIsMissing() {
        WeChatCodeExchange exchange =
                code -> new WeChatTokenResponse("openid-1", null, null, null);
        WeChatExternalIdentityVerifier verifier =
                new WeChatExternalIdentityVerifier(enabledProperties(), exchange);

        ExternalIdentity identity =
                verifier.verify(IdentityProvider.WECHAT, "one-time-code");

        assertThat(identity.subject())
                .isEqualTo("appid:wx-test:openid:openid-1");
        assertThat(identity.subjects())
                .containsExactly("appid:wx-test:openid:openid-1");
    }

    @Test
    void rejectsDisabledOrEmptyCredentialWithoutCallingWechat() {
        WeChatCodeExchange exchange =
                code -> {
                    throw new AssertionError("exchange must not be called");
                };
        WeChatProperties disabled =
                new WeChatProperties(
                        false,
                        "wx-test",
                        "server-secret",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5));
        WeChatExternalIdentityVerifier verifier =
                new WeChatExternalIdentityVerifier(disabled, exchange);

        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        IdentityProvider.WECHAT,
                                        "one-time-code"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode.AUTH_PROVIDER_UNAVAILABLE));

        WeChatExternalIdentityVerifier enabled =
                new WeChatExternalIdentityVerifier(enabledProperties(), exchange);
        assertThatThrownBy(
                        () ->
                                enabled.verify(
                                        IdentityProvider.WECHAT,
                                        " "))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode.AUTH_INVALID_CREDENTIAL));
    }

    @Test
    void rejectsWechatResponseWithoutStableIdentity() {
        WeChatCodeExchange exchange =
                code -> new WeChatTokenResponse(null, null, null, null);
        WeChatExternalIdentityVerifier verifier =
                new WeChatExternalIdentityVerifier(enabledProperties(), exchange);

        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        IdentityProvider.WECHAT,
                                        "one-time-code"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode.AUTH_PROVIDER_UPSTREAM_FAILED));
    }

    private static WeChatProperties enabledProperties() {
        return new WeChatProperties(
                true,
                "wx-test",
                "server-secret",
                Duration.ofSeconds(3),
                Duration.ofSeconds(5));
    }
}
