package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TaizhouServerAuthorityShuffleTest {
    @Test
    void identicalRoundRequestsDoNotProduceTheSameProductionWall() {
        QaTaizhouRoundEngine engine =
                new QaTaizhouRoundEngine(
                        new ObjectMapper(), TaizhouRoundMode.SERVER_AUTHORITY);
        QaTaizhouRoundEngine.Request request =
                QaTaizhouRoundEngineTest.request(
                        QaTaizhouRoundEngineTest.seats(false, true, true, true));

        QaTaizhouRoundResult first = engine.start(request);
        QaTaizhouRoundResult second = engine.start(request);

        assertThat(first.table().wall).hasSize(QaTaizhouTiles.WALL_SIZE);
        assertThat(second.table().wall).hasSize(QaTaizhouTiles.WALL_SIZE);
        assertThat(first.table().wall).isNotEqualTo(second.table().wall);
        assertThat(first.state().path("engineMode").asText()).isEqualTo("SERVER_AUTHORITY");
    }
}
