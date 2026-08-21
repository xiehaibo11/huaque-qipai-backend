package com.nanbei.entertainment.backend.roomtools.application;

/**
 * 预约下一局使用道具的结果。
 *
 * <p>原版预约本身不扣道具：{@code ChangeCard/Module.lua} 的 {@code reqChangeCard(RESERVED)} 只
 * 登记意图，真正的扣减发生在下一局生效时的 {@code sendRequestUseProps}，成功后状态才转为
 * {@code SUCCESS}。这里回带当前应付的支付方式和价格，供客户端画确认框文案。
 */
public record RoomToolReservationResponse(
        RoomToolType type,
        int targetRound,
        boolean active,
        RoomToolCurrency priceCurrency,
        long priceAmount,
        boolean replayed) {
    RoomToolReservationResponse asReplay() {
        return new RoomToolReservationResponse(
                type, targetRound, active, priceCurrency, priceAmount, true);
    }
}
