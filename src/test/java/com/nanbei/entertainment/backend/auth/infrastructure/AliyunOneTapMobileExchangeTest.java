package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.auth.application.VerifiedMobile;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class AliyunOneTapMobileExchangeTest {
    @Test
    void mapsSuccessfulGetMobileResponse() {
        DypnsClient client =
                (accessToken, outId) ->
                        new DypnsClient.Result(
                                "OK",
                                "13800138000",
                                "request-id");
        AliyunOneTapMobileExchange exchange =
                new AliyunOneTapMobileExchange(client);

        VerifiedMobile result =
                exchange.exchange(
                        "access-token", "trace-id");

        assertThat(result)
                .isEqualTo(
                        new VerifiedMobile(
                                "13800138000", "request-id"));
    }

    @Test
    void mapsNonOkResponseToInvalidCredential() {
        DypnsClient client =
                (accessToken, outId) ->
                        new DypnsClient.Result(
                                "InvalidToken", null, "request-id");
        AliyunOneTapMobileExchange exchange =
                new AliyunOneTapMobileExchange(client);

        assertThatThrownBy(
                        () ->
                                exchange.exchange(
                                        "bad-token", "trace-id"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .AUTH_INVALID_CREDENTIAL));
    }

    @Test
    void mapsMissingMobileToUpstreamFailure() {
        DypnsClient client =
                (accessToken, outId) ->
                        new DypnsClient.Result(
                                "OK", null, "request-id");
        AliyunOneTapMobileExchange exchange =
                new AliyunOneTapMobileExchange(client);

        assertThatThrownBy(
                        () ->
                                exchange.exchange(
                                        "access-token", "trace-id"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .AUTH_PROVIDER_UPSTREAM_FAILED));
    }

    @Test
    void mapsSdkExceptionToUpstreamFailureWithoutLeakingToken() {
        DypnsClient client =
                (accessToken, outId) -> {
                    throw new DypnsClient.RequestException(
                            "ServiceUnavailable",
                            "request-id",
                            new RuntimeException("provider failure"));
                };
        AliyunOneTapMobileExchange exchange =
                new AliyunOneTapMobileExchange(client);

        assertThatThrownBy(
                        () ->
                                exchange.exchange(
                                        "secret-access-token",
                                        "trace-id"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(
                                            ErrorCode
                                                    .AUTH_PROVIDER_UPSTREAM_FAILED);
                            assertThat(exception.getMessage())
                                    .doesNotContain(
                                            "secret-access-token");
                        });
    }

    @Test
    void mapsProviderTokenExceptionsToInvalidCredential() {
        String[] invalidTokenCodes = {
            "isv.ACCESS_CODE_ILLEGAL",
            "isv.TOKEN_INVALID",
            "isv.TOKEN_UNAUTHORIZED_USED"
        };

        for (String providerCode : invalidTokenCodes) {
            DypnsClient client =
                    (accessToken, outId) -> {
                        throw new DypnsClient.RequestException(
                                providerCode,
                                "request-id",
                                new RuntimeException(
                                        "provider rejected token"));
                    };
            AliyunOneTapMobileExchange exchange =
                    new AliyunOneTapMobileExchange(client);

            assertThatThrownBy(
                            () ->
                                    exchange.exchange(
                                            "secret-access-token",
                                            "trace-id"))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            exception -> {
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .AUTH_INVALID_CREDENTIAL);
                                assertThat(exception.getMessage())
                                        .doesNotContain(
                                                "secret-access-token");
                            });
        }
    }

    @Test
    void mapsNullResponseToUpstreamFailure() {
        AliyunOneTapMobileExchange exchange =
                new AliyunOneTapMobileExchange(
                        (accessToken, outId) -> null);

        assertThatThrownBy(
                        () ->
                                exchange.exchange(
                                        "access-token", "trace-id"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .AUTH_PROVIDER_UPSTREAM_FAILED));
    }
}
