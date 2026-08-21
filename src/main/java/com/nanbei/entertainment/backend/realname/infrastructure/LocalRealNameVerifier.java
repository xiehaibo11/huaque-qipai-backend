package com.nanbei.entertainment.backend.realname.infrastructure;

import com.nanbei.entertainment.backend.realname.application.RealNameVerifier;
import com.nanbei.entertainment.backend.realname.application.RealNameVerifyResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local Profile 的实名核验桩：约定测试号 110101199001011237
 * （前十七位 11010119900101123 按 GB 11643 权重 7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2
 * 加权和 126，126 % 11 = 5，余数映射 10X98765432 得校验位 7）
 * 搭配任意非空姓名返回 MATCH，其余返回 MISMATCH。
 */
@Component
@Profile("local")
public class LocalRealNameVerifier implements RealNameVerifier {
    static final String LOCAL_MATCH_ID_CARD = "110101199001011237";

    @Override
    public RealNameVerifyResult verify(
            String realName, String idCardNumber) {
        if (LOCAL_MATCH_ID_CARD.equals(idCardNumber)
                && realName != null
                && !realName.isBlank()) {
            return RealNameVerifyResult.MATCH;
        }
        return RealNameVerifyResult.MISMATCH;
    }
}
