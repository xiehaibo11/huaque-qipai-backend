package com.nanbei.entertainment.backend.roomtools.application;

/**
 * 等待桌上的两个道具入口。
 *
 * <p>价格不再挂在枚举上：原版按玩家持有的道具与余额在三种支付方式之间切换，取值见
 * {@link RoomToolPricing}。
 */
public enum RoomToolType {
    CHANGE_CARD("换牌"),
    SHUFFLE("洗牌");

    private final String displayName;

    RoomToolType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
