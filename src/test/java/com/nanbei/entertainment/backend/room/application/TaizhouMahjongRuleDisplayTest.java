package com.nanbei.entertainment.backend.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class TaizhouMahjongRuleDisplayTest {
    @Test
    void followsTheOriginalTaizhouRuleOrderAndScreenshotWording() {
        String display =
                TaizhouMahjongRuleDisplay.render(
                        "winLostType='1';FengDing='0';PayType='0';basescore='1';IsSysTrust='0';",
                        2,
                        8,
                        RoomPayType.ALL);

        assertThat(display).isEqualTo("不平搓/不封顶/房主消耗/2人/底分1/8局");
    }

    @Test
    void keepsEnabledRulesAndFourPlayerCircleCount() {
        String display =
                TaizhouMahjongRuleDisplay.render(
                        "winLostType='1';forceGPS='1';liaoDaZiBaoPai='1';"
                                + "buSiBao='1';FengDing='0';PayType='0';IsSysTrust='60';",
                        4,
                        2,
                        RoomPayType.ALL);

        assertThat(display)
                .isEqualTo(
                        "不平搓/防作弊/撩搭子包牌/不死包/不封顶/房主消耗/4人/2圈/超时60秒托管");
    }

    @Test
    void recognizesTheOriginalAutoReadyRuleForms() {
        assertThat(TaizhouMahjongRuleDisplay.isAutoReady("autoReady='1';"))
                .isTrue();
        assertThat(
                        TaizhouMahjongRuleDisplay.isAutoReady(
                                "UserRule='AutoReady=true;';"))
                .isTrue();
    }

    @Test
    void rejectsDisabledOrMalformedAutoReadyRules() {
        assertThat(TaizhouMahjongRuleDisplay.isAutoReady("autoReady='0';"))
                .isFalse();
        assertThat(
                        TaizhouMahjongRuleDisplay.isAutoReady(
                                "UserRule='AutoReady=false;';"))
                .isFalse();
        assertThat(TaizhouMahjongRuleDisplay.isAutoReady("autoReady='true';"))
                .isFalse();
        assertThat(TaizhouMahjongRuleDisplay.isAutoReady(""))
                .isFalse();
    }
}
