package com.nanbei.entertainment.backend.room.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RoomRuleAssemblerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoomRuleAssembler assembler = new RoomRuleAssembler();

    @Test
    void assemblesRadiosBeforeCheckboxesAndAppendsRoomFee() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {
                          "version": 1,
                          "costRelativeToPlayers": false,
                          "trailingRule": "basescore='1';",
                          "groups": [
                            {
                              "key": "1roomType",
                              "order": 1,
                              "type": "RADIO",
                              "defaults": ["RoomType='0';"],
                              "lines": [{"options": [
                                {"node": "RoomType='0';", "text": "自摸"},
                                {"node": "RoomType='1';", "text": "点炮"}
                              ]}]
                            },
                            {
                              "key": "2rules",
                              "order": 2,
                              "type": "CHECKBOX",
                              "defaults": ["CanChow"],
                              "lines": [{"options": [
                                {"node": "CanChow", "text": "可以吃牌", "select": "CanChow='1';", "unselect": "CanChow='0';"},
                                {"node": "LmtMarker", "text": "禁用记牌器", "select": "LmtMarker='1';", "unselect": "LmtMarker='0';"}
                              ]}]
                            },
                            {
                              "key": "3playerCount",
                              "order": 3,
                              "type": "RADIO",
                              "counter": "PLAYER_COUNT",
                              "defaults": ["playerCount_4"],
                              "lines": [{"options": [{"node": "playerCount_4", "text": "4人", "value": 4}]}]
                            },
                            {
                              "key": "4playCount",
                              "order": 4,
                              "type": "RADIO",
                              "counter": "PLAY_COUNT",
                              "defaults": ["playCount_8"],
                              "lines": [{"options": [{"node": "playCount_8", "text": "8局", "value": 8,
                                "costs": {"ALL": {"0": 200}, "AA": {"0": 50}}}]}]
                            },
                            {
                              "key": "5payType",
                              "order": 5,
                              "type": "RADIO",
                              "counter": "PAY_TYPE",
                              "defaults": ["PayType='0';"],
                              "lines": [{"options": [
                                {"node": "PayType='0';", "text": "房主支付", "costType": "ALL"},
                                {"node": "PayType='1';", "text": "平摊支付", "costType": "AA"}
                              ]}]
                            }
                          ]
                        }
                        """);

        RoomRuleSelection result =
                assembler.assemble(
                        config,
                        1,
                        List.of(
                                "RoomType='1';",
                                "CanChow",
                                "playerCount_4",
                                "playCount_8",
                                "PayType='0';"));

        assertThat(result.gameRule())
                .isEqualTo(
                        "RoomType='1';PayType='0';CanChow='1';LmtMarker='0';basescore='1';RoomFee='2';");
        assertThat(result.playerCount()).isEqualTo(4);
        assertThat(result.playCount()).isEqualTo(8);
        assertThat(result.payType()).isEqualTo(RoomPayType.ALL);
        assertThat(result.roomFeeCenti()).isEqualTo(200);
    }

    @Test
    void usesSelectedDropdownNodeAndCentiFractionForAaPayment() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {
                          "version": 1,
                          "costRelativeToPlayers": false,
                          "trailingRule": "",
                          "groups": [
                            {"key":"1playerCount","order":1,"type":"RADIO","counter":"PLAYER_COUNT","defaults":["playerCount_4"],"lines":[{"options":[{"node":"playerCount_4","text":"4人","value":4}]}]},
                            {"key":"2playCount","order":2,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_24"],"lines":[{"options":[{"node":"playCount_24","text":"24局","value":24,"costs":{"ALL":{"0":600},"AA":{"0":150}}}]}]},
                            {"key":"3payType","order":3,"type":"RADIO","counter":"PAY_TYPE","defaults":["PayType='0';"],"lines":[{"options":[{"node":"PayType='0';","text":"房主支付","costType":"ALL"},{"node":"PayType='1';","text":"平摊支付","costType":"AA"}]}]},
                            {"key":"4trust","order":4,"type":"RADIO","defaults":["IsSysTrust='15';"],"lines":[{"options":[{"node":"IsSysTrust='15';","text":"15秒"},{"node":"IsSysTrust=other","text":"其他","dropdown":[{"node":"IsSysTrust='60';","text":"60秒"},{"node":"IsSysTrust='300';","text":"300秒"}],"dropdownDefault":"IsSysTrust='60';"}]}]}
                          ]
                        }
                        """);

        RoomRuleSelection result =
                assembler.assemble(
                        config,
                        1,
                        List.of(
                                "playerCount_4",
                                "playCount_24",
                                "PayType='1';",
                                "IsSysTrust=other",
                                "IsSysTrust='300';"));

        assertThat(result.gameRule())
                .isEqualTo("PayType='1';IsSysTrust='300';RoomFee='1.5';");
        assertThat(result.roomFeeCenti()).isEqualTo(150);
        assertThat(result.payType()).isEqualTo(RoomPayType.AA);
    }

    @Test
    void rejectsUnknownAndMultipleRadioSelections() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {"version":1,"costRelativeToPlayers":false,"trailingRule":"","groups":[
                          {"key":"1playerCount","order":1,"type":"RADIO","counter":"PLAYER_COUNT","defaults":["playerCount_2"],"lines":[{"options":[{"node":"playerCount_2","text":"2人","value":2},{"node":"playerCount_3","text":"3人","value":3}]}]},
                          {"key":"2playCount","order":2,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_8"],"lines":[{"options":[{"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":100}}}]}]},
                          {"key":"3payType","order":3,"type":"RADIO","counter":"PAY_TYPE","defaults":["PayType='0';"],"lines":[{"options":[{"node":"PayType='0';","text":"房主支付","costType":"ALL"}]}]}
                        ]}
                        """);

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config,
                                        1,
                                        List.of(
                                                "playerCount_2",
                                                "playerCount_3",
                                                "playCount_8",
                                                "PayType='0';")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("单选");
        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config,
                                        1,
                                        List.of(
                                                "playerCount_2",
                                                "playCount_8",
                                                "PayType='0';",
                                                "Forged='1';")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("未知");
    }

    @Test
    void rejectsSelectingAnOptionProhibitedByTheActiveLinkageLevel() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {"version":1,"costRelativeToPlayers":false,"trailingRule":"","groups":[
                          {"key":"1mode","order":1,"type":"RADIO","defaults":["Mode='1';"],"lines":[{"options":[{"node":"Mode='1';","text":"模式一","linkageLevel":1},{"node":"Mode='2';","text":"模式二","linkageLevel":2}]}]},
                          {"key":"2rules","order":2,"type":"CHECKBOX","defaults":[],"lines":[{"options":[{"node":"Forbidden","text":"禁用项","select":"Forbidden='1';","unselect":"Forbidden='0';","prohibit":[1]}]}]},
                          {"key":"3playerCount","order":3,"type":"RADIO","counter":"PLAYER_COUNT","defaults":["playerCount_2"],"lines":[{"options":[{"node":"playerCount_2","text":"2人","value":2}]}]},
                          {"key":"4playCount","order":4,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_8"],"lines":[{"options":[{"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":100}}}]}]}
                        ]}
                        """);

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config,
                                        1,
                                        List.of(
                                                "Mode='1';",
                                                "Forbidden",
                                                "playerCount_2",
                                                "playCount_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    void appendsMustExistRuleAfterCheckboxRules() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {"version":1,"costRelativeToPlayers":false,"trailingRule":"","groups":[
                          {"key":"1rules","order":1,"type":"CHECKBOX","defaults":["DelColor"],"lines":[{"options":[{"node":"DelColor","text":"缺色","select":"DelColorUi='1';","unselect":"DelColorUi='0';","mustExistRule":"DelColor","mustExistRuleYes":"DelColor='1';","mustExistRuleNo":"DelColor='0';"}]}]},
                          {"key":"2playerCount","order":2,"type":"RADIO","counter":"PLAYER_COUNT","defaults":["playerCount_2"],"lines":[{"options":[{"node":"playerCount_2","text":"2人","value":2}]}]},
                          {"key":"3playCount","order":3,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_8"],"lines":[{"options":[{"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":25}}}]}]}
                        ]}
                        """);

        RoomRuleSelection selected =
                assembler.assemble(
                        config,
                        1,
                        List.of("DelColor", "playerCount_2", "playCount_8"));
        RoomRuleSelection unselected =
                assembler.assemble(
                        config, 1, List.of("playerCount_2", "playCount_8"));

        assertThat(selected.gameRule())
                .isEqualTo("DelColorUi='1';DelColor='1';RoomFee='0.25';");
        assertThat(unselected.gameRule())
                .isEqualTo("DelColorUi='0';DelColor='0';RoomFee='0.25';");
    }

    @Test
    void rejectsSelectingAnOptionWhoseShowLevelIsNotActive() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {"version":1,"costRelativeToPlayers":false,"trailingRule":"","groups":[
                          {"key":"1mode","order":1,"type":"RADIO","defaults":["Mode='1';"],"lines":[{"options":[{"node":"Mode='1';","text":"模式一","linkageLevel":1},{"node":"Mode='2';","text":"模式二","linkageLevel":2}]}]},
                          {"key":"2rules","order":2,"type":"CHECKBOX","defaults":[],"lines":[{"options":[{"node":"OnlyModeTwo","text":"模式二专用","select":"OnlyModeTwo='1';","unselect":"OnlyModeTwo='0';","show":[2]}]}]},
                          {"key":"3playCount","order":3,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_8"],"lines":[{"options":[{"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":100}}}]}]}
                        ]}
                        """);

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config,
                                        1,
                                        List.of("Mode='1';", "OnlyModeTwo", "playCount_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("不可见");
    }

    @Test
    void usesSelectedCostCarrierAndOriginalFallbackCountsWhenCountersAreAbsent()
            throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {"version":0,"costRelativeToPlayers":false,"trailingRule":"","defaultCategoryIndex":1,"categories":[
                          {"index":1,"groups":[
                            {"key":"1RoomType","order":1,"title":"玩法","type":"RADIO","counter":null,"defaults":["gameType='1';"],"lines":[{"options":[
                              {"node":"gameType='1';","text":"打板","categoryIndex":1},
                              {"node":"gameType='0';","text":"推倒胡","categoryIndex":2},
                              {"node":"gameType='2';","text":"洗脑","categoryIndex":3}
                            ]}]},
                            {"key":"2playerCount","order":2,"title":"人数","type":"RADIO","counter":"PLAYER_COUNT","defaults":["playerCount_4"],"lines":[{"options":[
                              {"node":"playerCount_4","text":"4人","costs":{"ALL":{"0":200}},"value":4}
                            ]}]},
                            {"key":"6other","order":6,"title":"功能","type":"CHECKBOX","counter":null,"defaults":[],"lines":[{"options":[
                              {"node":"AutoReady","text":"自动准备","select":"UserRule='AutoReady=true;';","unselect":"UserRule='AutoReady=false;';"},
                              {"node":"IsSysTrust","text":"自动托管","select":"IsSysTrust='1';","unselect":"IsSysTrust='0';"}
                            ]}]}
                          ]}
                        ]}
                        """);

        RoomRuleSelection result =
                assembler.assemble(
                        config,
                        1,
                        List.of("gameType='1';", "playerCount_4"));

        assertThat(result.gameRule())
                .isEqualTo(
                        "gameType='1';UserRule='AutoReady=false;';IsSysTrust='0';RoomFee='2';");
        assertThat(result.playerCount()).isEqualTo(4);
        assertThat(result.playCount()).isEqualTo(8);
        assertThat(result.payType()).isEqualTo(RoomPayType.ALL);
        assertThat(result.roomFeeCenti()).isEqualTo(200);
    }

    @Test
    void derivesOriginalCreateProtocolConditionsAndRoomMode() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {"version":1,"costRelativeToPlayers":false,"trailingRule":"","groups":[
                          {"key":"1RoomType","order":1,"type":"RADIO","defaults":["gameType='1';"],"lines":[{"options":[
                            {"node":"gameType='1';","text":"打板","condition":"ConditionRoomType","conditionYes":"DaBan"},
                            {"node":"gameType='2';","text":"洗脑","condition":"ConditionRoomType","conditionYes":"XiNao"}
                          ]}]},
                          {"key":"2playerCount","order":2,"type":"RADIO","counter":"PLAYER_COUNT","defaults":["playerCount_4"],"lines":[{"options":[
                            {"node":"playerCount_4","text":"4人","value":4}
                          ]}]},
                          {"key":"3playCount","order":3,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_4"],"lines":[{"options":[
                            {"node":"playCount_4","text":"4局","value":4,"costs":{"ALL":{"0":100},"AA":{"0":25}}},
                            {"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":200},"AA":{"0":50}}},
                            {"node":"playCount_16","text":"16局","value":16,"costs":{"ALL":{"0":400},"AA":{"0":100}}},
                            {"node":"playCount_24","text":"24局","value":24,"costs":{"ALL":{"0":600},"AA":{"0":150}}}
                          ]}]},
                          {"key":"4payType","order":4,"type":"RADIO","counter":"PAY_TYPE","defaults":["PayType='0';"],"lines":[{"options":[
                            {"node":"PayType='0';","text":"房主支付","costType":"ALL"},
                            {"node":"PayType='1';","text":"平摊支付","costType":"AA"}
                          ]}]}
                        ]}
                        """);

        RoomRuleSelection result =
                assembler.assemble(
                        config,
                        1,
                        List.of("gameType='2';", "playerCount_4", "playCount_8", "PayType='1';"));

        assertThat(result.roomMode()).isEqualTo(6);
        assertThat(result.roomConditions()).containsEntry("ConditionRoomType", "XiNao");
        assertThat(OriginalRoomRuleAssembler.assemble(30300L, result))
                .isEqualTo(
                        "roomrule={GamePlayerCount=\"4\",group=\"30300\",cancreate=\"1\",roommode=\"10\",ConditionRoomType=\"XiNao\"}");
    }

    @Test
    void rejectsCategorySelectorThatDoesNotMatchRequestedCategory() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {"version":1,"costRelativeToPlayers":false,"trailingRule":"","defaultCategoryIndex":1,"categories":[
                          {"index":1,"groups":[
                            {"key":"1mode","order":1,"type":"RADIO","defaults":["Mode='1';"],"lines":[{"options":[{"node":"Mode='1';","text":"模式一","categoryIndex":1},{"node":"Mode='2';","text":"模式二","categoryIndex":2}]}]},
                            {"key":"2playCount","order":2,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_8"],"lines":[{"options":[{"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":100}}}]}]}
                          ]},
                          {"index":2,"groups":[
                            {"key":"1mode","order":1,"type":"RADIO","defaults":["Mode='2';"],"lines":[{"options":[{"node":"Mode='1';","text":"模式一","categoryIndex":1},{"node":"Mode='2';","text":"模式二","categoryIndex":2}]}]},
                            {"key":"2playCount","order":2,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_8"],"lines":[{"options":[{"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":100}}}]}]}
                          ]}
                        ]}
                        """);

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config, 1, List.of("Mode='2';", "playCount_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("分类");
    }
}
