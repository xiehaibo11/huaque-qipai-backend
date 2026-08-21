package com.nanbei.entertainment.backend.timeloginact.domain;

/**
 * 时段奖励状态，逐值对应原版 {@code lobby/Modules/TimeLoginAct/Config.lua:4-10} 的
 * {@code TimeLoginActConfig.STATUS}。线协议使用原版字面量，Android 直接按同名分支渲染。
 */
public enum TimeLoginRewardStatus {
    CAN_REWARD("CanReward"),
    REWARDED("Rewarded"),
    CAN_SUPPLE("CanSupple"),
    NOT_IN_TIME("NotInTime"),
    OVER_TIME("OverTime");

    private final String wireValue;

    TimeLoginRewardStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
