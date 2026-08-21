package com.nanbei.entertainment.backend.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

final class RoomRuleDisplayTest {
    private final RoomRuleAssembler assembler = new RoomRuleAssembler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assemblesHumanReadableGameChoicesWithoutDuplicatingRoomMetadata() throws Exception {
        var config =
                objectMapper.readTree(
                        """
                        {"groups":[
                          {"key":"mode","order":1,"type":"RADIO","lines":[{"options":[
                            {"node":"mode1","text":"不平搓"},{"node":"mode2","text":"平搓"}]}]},
                          {"key":"rules","order":2,"type":"CHECKBOX","lines":[{"options":[
                            {"node":"bao","text":"撩搭子包牌","select":"bao=1;","unselect":"bao=0;"}]}]},
                          {"key":"players","order":3,"type":"RADIO","counter":"PLAYER_COUNT","lines":[{"options":[
                            {"node":"p4","text":"4人","value":4}]}]},
                          {"key":"rounds","order":4,"type":"RADIO","counter":"PLAY_COUNT","lines":[{"options":[
                            {"node":"r2","text":"2圈","value":2,"costs":{"ALL":{"0":400}}}]}]},
                          {"key":"pay","order":5,"type":"RADIO","counter":"PAY_TYPE","lines":[{"options":[
                            {"node":"pay0","text":"房主支付","costType":"ALL"}]}]},
                          {"key":"trust","order":6,"type":"RADIO","lines":[{"options":[
                            {"node":"trust0","text":"不托管"},
                            {"node":"trustOther","text":"其他","dropdown":[
                              {"node":"trust60","text":"60秒"},{"node":"trust120","text":"120秒"}]}]}]}
                        ]}
                        """);

        RoomRuleSelection result =
                assembler.assemble(
                        config,
                        1,
                        List.of("mode1", "bao", "p4", "r2", "pay0", "trustOther", "trust60"));

        assertThat(result.gameRuleDisplay()).isEqualTo("不平搓/撩搭子包牌/60秒");
    }
}
