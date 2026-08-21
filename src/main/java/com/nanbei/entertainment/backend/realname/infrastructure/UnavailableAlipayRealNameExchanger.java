package com.nanbei.entertainment.backend.realname.infrastructure;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.realname.application.AlipayRealName;
import com.nanbei.entertainment.backend.realname.application.AlipayRealNameExchanger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
@ConditionalOnProperty(
        prefix = "nanbei.alipay-realname",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class UnavailableAlipayRealNameExchanger
        implements AlipayRealNameExchanger {
    @Override
    public AlipayRealName exchange(String authCode) {
        throw new ApiException(
                ErrorCode.REALNAME_UNAVAILABLE,
                "支付宝实名认证服务尚未启用");
    }
}
