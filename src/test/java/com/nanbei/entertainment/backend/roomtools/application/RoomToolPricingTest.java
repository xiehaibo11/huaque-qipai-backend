package com.nanbei.entertainment.backend.roomtools.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomToolPricingTest {
    @Test
    @DisplayName("30109 洗牌是 100 小房卡，来自 GameSub.lua 与 900023 区配置")
    void shuffleCostsOneHundredSmallRoomCards() {
        // GameSub.lua:104 -> ShufflePropID = 131, ShufflePropCount = 100
        // AreaConfig.lua:282-308 -> smallRoomCardID = 131, smallRoomCardRatio = 100
        assertEquals(100L, RoomToolPricing.SHUFFLE_ROOM_CARD_CENTI);
        assertEquals(
                100L,
                RoomToolPricing.amount(RoomToolType.SHUFFLE, RoomToolCurrency.ROOM_CARD));
    }

    @Test
    @DisplayName("持有券时优先用券，对应 getShowType 的第一分支")
    void ticketWinsOverEveryBalance() {
        assertEquals(
                RoomToolCurrency.TICKET,
                RoomToolPricing.selectCurrency(RoomToolType.CHANGE_CARD, 1, 999_999, 999_999));
        assertEquals(
                RoomToolCurrency.TICKET,
                RoomToolPricing.selectCurrency(RoomToolType.SHUFFLE, 3, 0, 0));
        assertEquals(
                1L, RoomToolPricing.amount(RoomToolType.CHANGE_CARD, RoomToolCurrency.TICKET));
    }

    @Test
    @DisplayName("换牌无券时按钻石优先、房卡次之，最后回落钻石")
    void changeCardFollowsTheOriginalPriority() {
        assertEquals(
                RoomToolCurrency.DIAMOND,
                RoomToolPricing.selectCurrency(
                        RoomToolType.CHANGE_CARD, 0, 999_999, RoomToolPricing.CHANGE_CARD_DIAMOND));
        assertEquals(
                RoomToolCurrency.ROOM_CARD,
                RoomToolPricing.selectCurrency(
                        RoomToolType.CHANGE_CARD,
                        0,
                        RoomToolPricing.CHANGE_CARD_ROOM_CARD_CENTI,
                        0));
        // 两边都不够时原版回落到钻石，按钮随后显示余额不足。
        assertEquals(
                RoomToolCurrency.DIAMOND,
                RoomToolPricing.selectCurrency(RoomToolType.CHANGE_CARD, 0, 0, 0));
    }

    @Test
    @DisplayName("洗牌没有钻石档，无券时直接落到小房卡")
    void shuffleHasNoDiamondTier() {
        assertEquals(
                RoomToolCurrency.ROOM_CARD,
                RoomToolPricing.selectCurrency(RoomToolType.SHUFFLE, 0, 0, 999_999));
        assertEquals(0L, RoomToolPricing.amount(RoomToolType.SHUFFLE, RoomToolCurrency.DIAMOND));
    }

    @Test
    @DisplayName("余额判定对应原版 isNotEnough 的反面")
    void affordabilityChecksTheSelectedCurrencyOnly() {
        assertTrue(
                RoomToolPricing.affordable(
                        RoomToolType.SHUFFLE, RoomToolCurrency.ROOM_CARD, 0, 100, 0));
        assertFalse(
                RoomToolPricing.affordable(
                        RoomToolType.SHUFFLE, RoomToolCurrency.ROOM_CARD, 0, 99, 999_999));
        assertTrue(
                RoomToolPricing.affordable(
                        RoomToolType.CHANGE_CARD, RoomToolCurrency.TICKET, 1, 0, 0));
    }

    @Test
    @DisplayName("券种道具编码对应区配置的 150729 换牌卡与 150188 洗牌道具")
    void ticketItemsMapToTheOriginalPropIds() {
        assertEquals(
                RoomToolPricing.CHANGE_CARD_TICKET_ITEM,
                RoomToolPricing.ticketItem(RoomToolType.CHANGE_CARD));
        assertEquals(
                RoomToolPricing.SHUFFLE_TICKET_ITEM,
                RoomToolPricing.ticketItem(RoomToolType.SHUFFLE));
    }
}
