package com.nanbei.entertainment.backend.room.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RoomRuleAssemblerHiddenRadioTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoomRuleAssembler assembler = new RoomRuleAssembler();

    @Test
    void accepts30111FlatRubWhenTheFollowingRadioIsEntirelyHidden() throws Exception {
        RoomRuleSelection selection =
                assembler.assemble(
                        config30111(),
                        1,
                        List.of("winLostType='3';", "playCount_8"));

        assertThat(selection.gameRule())
                .isEqualTo("winLostType='3';RoomFee='1';");
        assertThat(selection.playerCount()).isEqualTo(4);
        assertThat(selection.playCount()).isEqualTo(8);
    }

    @Test
    void accepts30113NoJokerWhenTheFollowingRadioIsEntirelyHidden() throws Exception {
        RoomRuleSelection selection =
                assembler.assemble(
                        config30113(),
                        1,
                        List.of("hasJoker='0';", "playCount_8"));

        assertThat(selection.gameRule()).isEqualTo("hasJoker='0';RoomFee='1';");
        assertThat(selection.playerCount()).isEqualTo(4);
        assertThat(selection.playCount()).isEqualTo(8);
    }

    @Test
    void stillRejectsSubmittingAnOptionFromAnEntirelyHiddenRadio() throws Exception {
        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config30113(),
                                        1,
                                        List.of(
                                                "hasJoker='0';",
                                                "huType='1';",
                                                "playCount_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("不可见");
    }

    @Test
    void stillRequiresExactlyOneSelectionWhenTheRadioHasVisibleOptions() throws Exception {
        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config30113(),
                                        1,
                                        List.of("hasJoker='1';", "playCount_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("单选");
    }

    private JsonNode config30111() throws Exception {
        return objectMapper.readTree(
                """
                {"version":23,"costRelativeToPlayers":false,"trailingRule":"","groups":[
                  {"key":"1gameRuleCheckBox","order":1,"type":"RADIO","defaults":["bupingcuo"],"lines":[{"options":[
                    {"node":"bupingcuo","text":"不平搓","linkageLevel":1},
                    {"node":"winLostType='3';","text":"平搓","linkageLevel":2}
                  ]}]},
                  {"key":"2gameRuleCheckBox","order":2,"type":"RADIO","defaults":["winLostType='1';"],"lines":[{"options":[
                    {"node":"winLostType='1';","text":"赢家搓","show":[1],"hide":[2]},
                    {"node":"winLostType='2';","text":"输家搓","show":[1],"hide":[2]}
                  ]}]},
                  {"key":"5playCount","order":5,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_8"],"lines":[{"options":[
                    {"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":100}}}
                  ]}]}
                ]}
                """);
    }

    private JsonNode config30113() throws Exception {
        return objectMapper.readTree(
                """
                {"version":23,"costRelativeToPlayers":false,"trailingRule":"","groups":[
                  {"key":"1gameRuleCheckBox","order":1,"type":"RADIO","defaults":["hasJoker='1';"],"lines":[{"options":[
                    {"node":"hasJoker='1';","text":"有财神","linkageLevel":1},
                    {"node":"hasJoker='0';","text":"无财神","linkageLevel":2}
                  ]}]},
                  {"key":"2gameRuleCheckBox","order":2,"type":"RADIO","defaults":["huType='1';"],"lines":[{"options":[
                    {"node":"huType='1';","text":"自摸胡","show":[1],"hide":[2]},
                    {"node":"huType='2';","text":"点炮胡","show":[1],"hide":[2]}
                  ]}]},
                  {"key":"5playCount","order":5,"type":"RADIO","counter":"PLAY_COUNT","defaults":["playCount_8"],"lines":[{"options":[
                    {"node":"playCount_8","text":"8局","value":8,"costs":{"ALL":{"0":100}}}
                  ]}]}
                ]}
                """);
    }
}
