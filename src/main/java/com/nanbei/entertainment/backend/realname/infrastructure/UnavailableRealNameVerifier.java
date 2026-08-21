package com.nanbei.entertainment.backend.realname.infrastructure;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.realname.application.RealNameVerifier;
import com.nanbei.entertainment.backend.realname.application.RealNameVerifyResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
@ConditionalOnProperty(
        prefix = "nanbei.realname",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class UnavailableRealNameVerifier implements RealNameVerifier {
    @Override
    public RealNameVerifyResult verify(
            String realName, String idCardNumber) {
        throw new ApiException(
                ErrorCode.REALNAME_UNAVAILABLE, "实名认证服务尚未启用");
    }
}
