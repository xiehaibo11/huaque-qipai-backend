package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GameplayFinalRoundPhaseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void totalResultCommitsTheSessionAsCompletedInTheSameRevision() {
        var state = objectMapper.createObjectNode();
        state.putObject("totalResult");
        assertThat(
                        GameplayCommandService.persistedPhase(
                                GamePhase.ROUND_RESULT, state))
                .isEqualTo(GamePhase.COMPLETED);
    }

    @Test
    void ordinaryRoundResultRemainsOpenForNextRound() {
        assertThat(
                        GameplayCommandService.persistedPhase(
                                GamePhase.ROUND_RESULT, objectMapper.createObjectNode()))
                .isEqualTo(GamePhase.ROUND_RESULT);
    }
}
