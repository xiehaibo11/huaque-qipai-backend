package com.nanbei.entertainment.backend.room.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RoomRuleAssemblerLinkageTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoomRuleAssembler assembler = new RoomRuleAssembler();

    @Test
    void rejectsDropdownNodeWhenItsParentRadioIsNotSelected() throws Exception {
        JsonNode config =
                config(
                        """
                        {"key":"2trust","order":2,"type":"RADIO","lines":[{"options":[
                          {"node":"Trust='15';","text":"15秒"},
                          {"node":"TrustOther","text":"其他","dropdown":[
                            {"node":"Trust='60';","text":"60秒"},
                            {"node":"Trust='300';","text":"300秒"}
                          ]}
                        ]}]}
                        """);

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config,
                                        1,
                                        List.of("Mode='1';", "Trust='15';", "Trust='60';", "rounds_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("未消费");
    }

    @Test
    void enforcesProhibitAndSelectForAnActiveLevel() throws Exception {
        JsonNode config =
                config(
                        checkboxGroup(
                                "Forced",
                                "\"prohibitAndSelect\":[1]"));

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config, 1, List.of("Mode='1';", "rounds_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("必须选中");

        RoomRuleSelection selected =
                assembler.assemble(
                        config, 1, List.of("Mode='1';", "Forced", "rounds_8"));
        assertThat(selected.gameRule()).contains("Forced='1';");
    }

    @Test
    void prohibitsOnlyWhenEveryProhibitMeanwhileLevelIsActive() throws Exception {
        JsonNode config =
                config(
                        """
                        {"key":"2secondMode","order":2,"type":"RADIO","lines":[{"options":[
                          {"node":"Second='2';","text":"二","linkageLevel":2},
                          {"node":"Second='0';","text":"无"}
                        ]}]},
                        %s
                        """
                                .formatted(
                                        checkboxGroup(
                                                "Combined",
                                                "\"prohibitMeanwhile\":[1,2]")));

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config,
                                        1,
                                        List.of(
                                                "Mode='1';",
                                                "Second='2';",
                                                "Combined",
                                                "rounds_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("禁用");

        assertThat(
                        assembler.assemble(
                                        config,
                                        1,
                                        List.of(
                                                "Mode='1';",
                                                "Second='0';",
                                                "Combined",
                                                "rounds_8"))
                                .gameRule())
                .contains("Combined='1';");
    }

    @Test
    void enforcesLinkSelectAndLinkProhibit() throws Exception {
        JsonNode config =
                config(
                        checkboxGroup("Linked", "\"linkSelect\":[1]")
                                + ","
                                + checkboxGroup("Blocked", "\"linkProhibit\":[1]"));

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config, 1, List.of("Mode='1';", "rounds_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("必须选中");
        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config,
                                        1,
                                        List.of(
                                                "Mode='1';", "Linked", "Blocked", "rounds_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("禁用");
        assertThat(
                        assembler.assemble(
                                        config,
                                        1,
                                        List.of("Mode='1';", "Linked", "rounds_8"))
                                .gameRule())
                .contains("Linked='1';Blocked='0';");
    }

    @Test
    void appliesLinkUnSelectFromAnUnselectedOptionsLinkageLevel() throws Exception {
        JsonNode config =
                config(
                        checkboxGroup(
                                "UnselectedBlocked",
                                "\"linkUnSelect\":[9]"));

        assertThatThrownBy(
                        () ->
                                assembler.assemble(
                                        config,
                                        1,
                                        List.of(
                                                "Mode='1';", "UnselectedBlocked", "rounds_8")))
                .isInstanceOf(RoomRuleValidationException.class)
                .hasMessageContaining("禁用");
        assertThat(
                        assembler.assemble(
                                        config,
                                        1,
                                        List.of(
                                                "Mode='2';", "UnselectedBlocked", "rounds_8"))
                                .gameRule())
                .contains("UnselectedBlocked='1';");
    }

    private JsonNode config(String extraGroups) throws Exception {
        return objectMapper.readTree(
                """
                {"version":1,"costRelativeToPlayers":false,"trailingRule":"","groups":[
                  {"key":"1mode","order":1,"type":"RADIO","lines":[{"options":[
                    {"node":"Mode='1';","text":"模式一","linkageLevel":1},
                    {"node":"Mode='2';","text":"模式二","unSelectlinkageLevel":9}
                  ]}]},
                  %s,
                  {"key":"99rounds","order":99,"type":"RADIO","counter":"PLAY_COUNT","lines":[{"options":[
                    {"node":"rounds_8","text":"8局","value":8,"costs":{"ALL":{"0":100}}}
                  ]}]}
                ]}
                """
                        .formatted(extraGroups));
    }

    private static String checkboxGroup(String node, String linkageField) {
        return """
                {"key":"rule%s","order":10,"type":"CHECKBOX","lines":[{"options":[
                  {"node":"%s","text":"%s","select":"%s='1';","unselect":"%s='0';",%s}
                ]}]}
                """
                .formatted(node, node, node, node, node, linkageField);
    }
}
