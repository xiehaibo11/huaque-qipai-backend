package com.nanbei.entertainment.backend.roomtools.application;

/**
 * 换牌/洗牌可用的支付方式，对应原版 {@code ChangeCardDefine.ShowType} 与区配置里的道具 ID。
 *
 * <p>台州麻将默认大厅 900023 的 {@code app/Config/AreaConfig.lua:282-308} 给出权威道具编号：
 * {@code roomCardID = 132}（房卡）、{@code smallRoomCardID = 131}（小房卡）、
 * {@code propDiamndID = 150000}（钻石）、{@code propShuffle = 150188}（洗牌道具）、
 * {@code propChangeCard = 150729}（换牌卡），并且 {@code smallRoomCardRatio = 100}，
 * 即一张房卡等于一百张小房卡。本项目钱包的 {@code room_card_centi} 就是小房卡单位。
 */
public enum RoomToolCurrency {
    /** 原版 ShowType.ROOM_CARD；金额以小房卡（centi）计。 */
    ROOM_CARD,
    /** 原版 ShowType.DIAMOND。 */
    DIAMOND,
    /** 原版 ShowType.CHANGE_CARD 的换牌卡，以及洗牌链路上的洗牌券，均为背包道具。 */
    TICKET
}
