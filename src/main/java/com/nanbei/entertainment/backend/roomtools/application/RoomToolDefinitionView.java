package com.nanbei.entertainment.backend.roomtools.application;

/**
 * 一个等待桌工具的当前可用状态。
 *
 * <p>{@code priceCurrency}/{@code priceAmount} 是按原版 {@code getShowType()} 优先级为当前玩家
 * 选出的支付方式与单次价格；{@code affordable} 对应原版 {@code isNotEnough()} 的反面。房卡金额以
 * 小房卡（centi）为单位，与区配置 {@code smallRoomCardRatio = 100} 一致。
 */
public record RoomToolDefinitionView(
        RoomToolType type,
        String displayName,
        RoomToolCurrency priceCurrency,
        long priceAmount,
        boolean affordable) {
    static RoomToolDefinitionView from(
            RoomToolType type, long ticketCount, long roomCardCenti, long diamonds) {
        RoomToolCurrency currency =
                RoomToolPricing.selectCurrency(type, ticketCount, roomCardCenti, diamonds);
        return new RoomToolDefinitionView(
                type,
                type.displayName(),
                currency,
                RoomToolPricing.amount(type, currency),
                RoomToolPricing.affordable(
                        type, currency, ticketCount, roomCardCenti, diamonds));
    }
}
