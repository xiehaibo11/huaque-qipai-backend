package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.OneTapMobileExchange;
import com.nanbei.entertainment.backend.auth.application.VerifiedMobile;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.util.Set;

public class AliyunOneTapMobileExchange
        implements OneTapMobileExchange {
    private static final String SUCCESS_CODE = "OK";
    private static final Set<String> INVALID_TOKEN_CODES =
            Set.of(
                    "isv.ACCESS_CODE_ILLEGAL",
                    "isv.TOKEN_INVALID",
                    "isv.TOKEN_UNAUTHORIZED_USED");

    private final DypnsClient client;

    public AliyunOneTapMobileExchange(DypnsClient client) {
        this.client = client;
    }

    @Override
    public VerifiedMobile exchange(
            String accessToken, String outId) {
        final DypnsClient.Result result;
        try {
            result = client.getMobile(accessToken, outId);
        } catch (DypnsClient.RequestException exception) {
            if (INVALID_TOKEN_CODES.contains(
                    exception.providerCode())) {
                throw invalidCredential();
            }
            throw upstreamFailure();
        } catch (RuntimeException exception) {
            throw upstreamFailure();
        }

        if (result == null) {
            throw upstreamFailure();
        }
        if (!SUCCESS_CODE.equals(result.code())) {
            throw invalidCredential();
        }
        if (result.mobile() == null
                || result.mobile().isBlank()) {
            throw upstreamFailure();
        }
        return new VerifiedMobile(
                result.mobile(), result.requestId());
    }

    private static ApiException upstreamFailure() {
        return new ApiException(
                ErrorCode.AUTH_PROVIDER_UPSTREAM_FAILED,
                "本机号码认证服务暂不可用，请稍后重试");
    }

    private static ApiException invalidCredential() {
        return new ApiException(
                ErrorCode.AUTH_INVALID_CREDENTIAL,
                "本机号码授权凭证无效或已过期");
    }
}
