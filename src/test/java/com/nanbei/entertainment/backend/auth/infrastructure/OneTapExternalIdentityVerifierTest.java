package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.auth.application.ExternalIdentity;
import com.nanbei.entertainment.backend.auth.application.OneTapMobileExchange;
import com.nanbei.entertainment.backend.auth.application.VerifiedMobile;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import org.junit.jupiter.api.Test;

class OneTapExternalIdentityVerifierTest {
    @Test
    void supportsOnlyOneTapProvider() {
        OneTapExternalIdentityVerifier verifier =
                verifierReturning("13800138000");

        assertThat(verifier.supports(IdentityProvider.ONE_TAP)).isTrue();
        assertThat(verifier.supports(IdentityProvider.WECHAT)).isFalse();
    }

    @Test
    void exchangesCarrierTokenForNormalizedPhoneIdentity() {
        OneTapExternalIdentityVerifier verifier =
                verifierReturning("+86 138-0013-8000");

        ExternalIdentity identity =
                verifier.verify(
                        IdentityProvider.ONE_TAP, "carrier-token");

        assertThat(identity)
                .isEqualTo(
                        new ExternalIdentity(
                                IdentityProvider.ONE_TAP,
                                "phone:13800138000"));
    }

    @Test
    void rejectsBlankCredentialBeforeExchange() {
        OneTapExternalIdentityVerifier verifier =
                verifierReturning("13800138000");

        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        IdentityProvider.ONE_TAP, " "))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .AUTH_INVALID_CREDENTIAL));
    }

    @Test
    void rejectsOversizedCredentialBeforeExchange() {
        OneTapExternalIdentityVerifier verifier =
                verifierReturning("13800138000");

        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        IdentityProvider.ONE_TAP,
                                        "x".repeat(4097)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .AUTH_INVALID_CREDENTIAL));
    }

    @Test
    void rejectsUnexpectedProvider() {
        OneTapExternalIdentityVerifier verifier =
                verifierReturning("13800138000");

        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        IdentityProvider.WECHAT,
                                        "carrier-token"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void mapsInvalidProviderMobileToUpstreamFailure() {
        OneTapExternalIdentityVerifier verifier =
                verifierReturning("not-a-mobile");

        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        IdentityProvider.ONE_TAP,
                                        "carrier-token"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .AUTH_PROVIDER_UPSTREAM_FAILED));
    }

    @Test
    void mapsMissingProviderResultToUpstreamFailure() {
        OneTapExternalIdentityVerifier verifier =
                new OneTapExternalIdentityVerifier(
                        (accessToken, outId) -> null);

        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        IdentityProvider.ONE_TAP,
                                        "carrier-token"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .AUTH_PROVIDER_UPSTREAM_FAILED));
    }

    private static OneTapExternalIdentityVerifier verifierReturning(
            String mobile) {
        OneTapMobileExchange exchange =
                (accessToken, outId) ->
                        new VerifiedMobile(
                                mobile, "provider-request-id");
        return new OneTapExternalIdentityVerifier(exchange);
    }
}
