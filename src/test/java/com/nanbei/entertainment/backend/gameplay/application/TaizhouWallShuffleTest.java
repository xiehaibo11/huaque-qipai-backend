package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaizhouWallShuffleTest {
    @Test
    void fixedSeedProducesAStableCompleteWallAndPublicCommitment() {
        byte[] seed = new byte[32];

        TaizhouWallShuffle.Result first = TaizhouWallShuffle.fromSeed(seed);
        TaizhouWallShuffle.Result second = TaizhouWallShuffle.fromSeed(seed);

        assertThat(first.algorithm()).isEqualTo("HMAC_SHA256_FISHER_YATES_V1");
        assertThat(first.seedSource()).isEqualTo("EXPLICIT_256_BIT_SEED");
        assertThat(first.commitment())
                .isEqualTo("fc70325cc0006df2761a0a18ca3b2f9913b25c5a60f12ed23f59a95764184f84");
        assertThat(first.wall()).isEqualTo(second.wall()).hasSize(QaTaizhouTiles.WALL_SIZE);

        Map<Integer, Integer> counts = new HashMap<>();
        first.wall().forEach(tile -> counts.merge(tile, 1, Integer::sum));
        assertThat(counts).hasSize(34);
        assertThat(counts.values()).containsOnly(4);
    }

    @Test
    void differentSeedsProduceDifferentWallsAndCommitments() {
        byte[] firstSeed = new byte[32];
        byte[] secondSeed = new byte[32];
        secondSeed[31] = 1;

        TaizhouWallShuffle.Result first = TaizhouWallShuffle.fromSeed(firstSeed);
        TaizhouWallShuffle.Result second = TaizhouWallShuffle.fromSeed(secondSeed);

        assertThat(first.wall()).isNotEqualTo(second.wall());
        assertThat(first.commitment()).isNotEqualTo(second.commitment());
    }

    @Test
    void productionEventPublishesCommitmentButNeverTheSeed() {
        QaTaizhouRoundEngine engine =
                new QaTaizhouRoundEngine(
                        new tools.jackson.databind.ObjectMapper(),
                        TaizhouRoundMode.SERVER_AUTHORITY);

        QaTaizhouRoundResult result =
                engine.start(
                        QaTaizhouRoundEngineTest.request(
                                QaTaizhouRoundEngineTest.seats(false, true, true, true)));

        GameEventView shuffled = wallShuffled(result.events());
        assertThat(shuffled.payload().get("algorithm"))
                .isEqualTo("HMAC_SHA256_FISHER_YATES_V1");
        assertThat(shuffled.payload().get("seedSource").toString())
                .startsWith("JCA_SECURE_RANDOM_256/");
        assertThat(shuffled.payload().get("commitment").toString()).matches("[0-9a-f]{64}");
        assertThat(shuffled.payload()).doesNotContainKeys("seed", "shuffleSeed");
        assertThat(result.state().path("shuffleCommitment").asText()).matches("[0-9a-f]{64}");
        assertThat(result.state().has("seed")).isFalse();
        assertThat(result.state().has("shuffleSeed")).isFalse();
        assertThat(result.state().path("qaRound").has("seed")).isFalse();
        assertThat(result.state().path("qaRound").has("shuffleSeed")).isFalse();

        QaRoundTable restored = engine.readTable(result.state());
        assertThat(restored.shuffleAlgorithm).isEqualTo(TaizhouWallShuffle.ALGORITHM);
        assertThat(restored.shuffleCommitment).isEqualTo(shuffled.payload().get("commitment"));
    }

    private static GameEventView wallShuffled(
            List<com.nanbei.entertainment.backend.gameplay.domain.GameEvent> events) {
        return events.stream()
                .filter(event -> event.type().equals("WALL_SHUFFLED"))
                .findFirst()
                .map(event -> new GameEventView(event.payload()))
                .orElseThrow();
    }

    private record GameEventView(Map<String, Object> payload) {}
}
