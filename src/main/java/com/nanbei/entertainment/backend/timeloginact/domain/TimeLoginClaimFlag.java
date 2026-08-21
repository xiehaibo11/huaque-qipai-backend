package com.nanbei.entertainment.backend.timeloginact.domain;

/**
 * 领奖结果码，逐值对应原版 {@code lobby/Modules/TimeLoginAct/Config.lua:18-26} 的
 * {@code TimeLoginActConfig.ClaimFlag}。原版 {@code Module.lua:98-110} 按这些值出提示，
 * 因此服务端必须返回同样的字面量。
 *
 * <p>{@code Box_Cnt_Lack} 属宝箱分支，当前实现范围不含宝箱（见证据台账第 10 节），
 * 因此不在本枚举内。
 */
public enum TimeLoginClaimFlag {
    SUCCESS("Success"),
    NOT_IN_TIME("Not_In_Time"),
    ALREADY_CLAIM("Already_Claim"),
    GOLD_OVER("Gold_Over"),
    WHEEL_CNT_LACK("Wheel_Cnt_Lack"),
    FAILED("Failed");

    private final String wireValue;

    TimeLoginClaimFlag(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
