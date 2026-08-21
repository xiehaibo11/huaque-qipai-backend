package com.nanbei.entertainment.backend.realname.infrastructure;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.realname.application.AlipayRealName;
import com.nanbei.entertainment.backend.realname.application.AlipayRealNameExchanger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local Profile 的支付宝实名换取桩：约定 authCode local-alipay-ok
 * 返回与 LocalRealNameVerifier 一致的测试实名信息。
 */
@Component
@Profile("local")
public class LocalAlipayRealNameExchanger
        implements AlipayRealNameExchanger {
    static final String LOCAL_AUTH_CODE = "local-alipay-ok";

    @Override
    public AlipayRealName exchange(String authCode) {
        if (!LOCAL_AUTH_CODE.equals(authCode)) {
            throw new ApiException(
                    ErrorCode.REALNAME_INVALID_FORMAT,
                    "支付宝授权凭证无效或已过期");
        }
        return new AlipayRealName(
                "张测试", LocalRealNameVerifier.LOCAL_MATCH_ID_CARD);
    }
}
