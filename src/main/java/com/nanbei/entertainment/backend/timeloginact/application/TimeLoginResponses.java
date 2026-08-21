package com.nanbei.entertainment.backend.timeloginact.application;

import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.util.List;

/**
 * 定时登录有礼的响应模型。字段名沿用原版 {@code Module.lua:42-57} 消费的
 * {@code loginRewards}/{@code goldOver}/{@code supplementCnt}/{@code wheelReward}
 * 语义，便于 Android 逐字段对照原版渲染分支；传输层是南北娱乐自研 REST，
 * 不是原版 {@code nyx/GetLoginReward} 报文。
 */
public final class TimeLoginResponses {
    private TimeLoginResponses() {}

    /** 一个奖励道具，对应原版 {@code props[]} 的 {@code propId}/{@code propCnt}/{@code name}。 */
    public record RewardItem(String propId, long propCnt, String name) {}

    /** 一个时段奖励，对应原版 {@code loginRewards[]} 元素。 */
    public record SlotView(
            String rewardId,
            int startTime,
            int endTime,
            String rewardFlag,
            List<RewardItem> props) {}

    /** 转盘，对应原版 {@code wheelReward[1]}；{@code props} 恒为 8 项且按格序。 */
    public record WheelView(
            String rewardId, int curCnt, int wheelCnt, List<RewardItem> props) {}

    /**
     * 活动状态。{@code daySecond} 与 {@code serverTime} 让 Android 按
     * {@code View.lua:428-451} 跑本地倒计时而不必自己猜时区。
     */
    public record StateResponse(
            String activityCode,
            long goldOver,
            int supplementCnt,
            int daySecond,
            long serverTime,
            List<SlotView> loginRewards,
            WheelView wheelReward,
            ShopWalletResponse wallet) {}

    /**
     * 领奖结果。{@code claimFlag} 是原版 {@code ClaimFlag} 字面量；
     * {@code wheelSliceIndex} 只在转盘抽取时非空，供客户端按
     * {@code Module.lua:262-275} 的语义直接定位停格。
     */
    public record ClaimResponse(
            String claimFlag,
            List<RewardItem> props,
            Integer wheelSliceIndex,
            ShopWalletResponse wallet) {}
}
