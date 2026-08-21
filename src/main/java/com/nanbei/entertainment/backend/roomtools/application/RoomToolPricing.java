package com.nanbei.entertainment.backend.roomtools.application;

/**
 * 复刻原版 {@code ChangeCardModule:getShowType()} 的支付方式优先级与 {@code getPrice()} 取值。
 *
 * <p>原版 {@code game/GameBase/Modules/ChangeCard/Module.lua:354-376} 的顺序是：持有换牌卡就用
 * 换牌卡；否则 {@code isShowRoomCardArea()} 命中就用房卡；否则钻石够就用钻石；否则房卡够就用
 * 房卡；都不够回落到钻石（此时按钮显示为余额不足）。{@code getPrice()} 只对房卡和钻石取服务端
 * 下发的 {@code changecard_roomcard_p} / {@code changecard_diamond_p}，换牌卡固定为 1。
 *
 * <p>洗牌的价格是原版事实：{@code app/Config/GameSub.lua:104} 给 30109 配
 * {@code ShufflePropID = 131, ShufflePropCount = 100}，131 是 900023 区的小房卡，
 * {@code smallRoomCardRatio = 100}，因此一次洗牌是 100 小房卡，正好等于一张房卡。
 * 洗牌另有 {@code ShufflePropQuanID = 150188} 的洗牌券免扣路径。
 *
 * <p>换牌的房卡价与钻石价在原版由服务端下发，归档里没有取值，因此下面两个常量是南北娱乐自定的
 * 配置值，不是已确认的原版数字；结构（三币种、优先级、换牌卡恒为 1）才是原版。
 */
public final class RoomToolPricing {
    /** 原版 30109 洗牌消耗：100 小房卡（= 1 房卡）。 */
    public static final long SHUFFLE_ROOM_CARD_CENTI = 100L;

    /** 南北娱乐配置值，对应原版服务端下发的 {@code changecard_roomcard_p}。 */
    public static final long CHANGE_CARD_ROOM_CARD_CENTI = 200L;

    /** 南北娱乐配置值，对应原版服务端下发的 {@code changecard_diamond_p}。 */
    public static final long CHANGE_CARD_DIAMOND = 20L;

    /** 换牌卡与洗牌券都按「一次一张」计，原版 {@code getPrice()} 对券种不覆盖默认值 1。 */
    public static final long TICKET_AMOUNT = 1L;

    /** 背包道具编码，对应区配置的 {@code propChangeCard = 150729}。 */
    public static final String CHANGE_CARD_TICKET_ITEM = "PROP_CHANGE_CARD_TICKET";

    /** 背包道具编码，对应区配置的 {@code propShuffle = 150188}。 */
    public static final String SHUFFLE_TICKET_ITEM = "PROP_SHUFFLE_TICKET";

    private RoomToolPricing() {}

    /** 某个工具的券种道具编码。 */
    public static String ticketItem(RoomToolType type) {
        return type == RoomToolType.CHANGE_CARD ? CHANGE_CARD_TICKET_ITEM : SHUFFLE_TICKET_ITEM;
    }

    /** 该工具用某种支付方式时的单次价格。 */
    public static long amount(RoomToolType type, RoomToolCurrency currency) {
        return switch (currency) {
            case TICKET -> TICKET_AMOUNT;
            case DIAMOND -> type == RoomToolType.CHANGE_CARD ? CHANGE_CARD_DIAMOND : 0L;
            case ROOM_CARD ->
                    type == RoomToolType.CHANGE_CARD
                            ? CHANGE_CARD_ROOM_CARD_CENTI
                            : SHUFFLE_ROOM_CARD_CENTI;
        };
    }

    /**
     * 按原版优先级挑出本次应付的支付方式。
     *
     * <p>洗牌没有钻石档（{@code GameSub.lua} 只配了小房卡与洗牌券），因此券不足时直接落到房卡。
     */
    public static RoomToolCurrency selectCurrency(
            RoomToolType type, long ticketCount, long roomCardCenti, long diamonds) {
        if (ticketCount >= TICKET_AMOUNT) {
            return RoomToolCurrency.TICKET;
        }
        if (type == RoomToolType.SHUFFLE) {
            return RoomToolCurrency.ROOM_CARD;
        }
        if (diamonds >= CHANGE_CARD_DIAMOND) {
            return RoomToolCurrency.DIAMOND;
        }
        if (roomCardCenti >= CHANGE_CARD_ROOM_CARD_CENTI) {
            return RoomToolCurrency.ROOM_CARD;
        }
        return RoomToolCurrency.DIAMOND;
    }

    /** 原版 {@code isNotEnough()}：按当前选中的支付方式判断余额。 */
    public static boolean affordable(
            RoomToolType type,
            RoomToolCurrency currency,
            long ticketCount,
            long roomCardCenti,
            long diamonds) {
        long price = amount(type, currency);
        return switch (currency) {
            case TICKET -> ticketCount >= price;
            case DIAMOND -> diamonds >= price;
            case ROOM_CARD -> roomCardCenti >= price;
        };
    }
}
