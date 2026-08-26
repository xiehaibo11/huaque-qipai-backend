package com.nanbei.entertainment.backend.gameplay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameplayPersistenceEntityTest {
    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID SESSION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void sessionAdvancesOnlyToTheNextRevision() {
        GameSessionEntity session = new GameSessionEntity(SESSION_ID, ROOM_ID, 30109L, NOW);

        session.advance(GamePhase.WAITING, 0, 1L, "{\"ready\":true}", NOW.plusSeconds(1));

        assertThat(session.getRevision()).isEqualTo(1L);
        assertThat(session.getState()).isEqualTo("{\"ready\":true}");
        assertThatThrownBy(
                        () ->
                                session.advance(
                                        GamePhase.WAITING,
                                        0,
                                        3L,
                                        "{}",
                                        NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("next revision");
    }

    @Test
    void seatAcknowledgementNeverMovesBackwards() {
        GameSessionSeatEntity seat =
                new GameSessionSeatEntity(SESSION_ID, 1, USER_ID, NOW);

        seat.setReady(true, NOW.plusSeconds(1));
        seat.acknowledge(4L, NOW.plusSeconds(2));
        seat.acknowledge(3L, NOW.plusSeconds(3));

        assertThat(seat.isReady()).isTrue();
        assertThat(score(seat)).isEqualTo(1000L);
        assertThat(seat.getLastAckRevision()).isEqualTo(4L);
    }

    @Test
    void goldSeatStartsFromThePlayersRealCoinBalance() {
        GameSessionSeatEntity seat =
                new GameSessionSeatEntity(SESSION_ID, 1, USER_ID, 18_765L, NOW);

        assertThat(seat.getScore()).isEqualTo(18_765L);
    }

    @Test
    void commandStoresTheAcceptedRevisionAndResponseOnce() {
        GameCommandEntity command =
                new GameCommandEntity(
                        SESSION_ID,
                        USER_ID,
                        "ready-1",
                        "a".repeat(64),
                        "READY",
                        0L,
                        NOW);

        command.accept(1L, "{\"revision\":1}");

        assertThat(command.getAcceptedRevision()).isEqualTo(1L);
        assertThat(command.getResult()).isEqualTo("{\"revision\":1}");
        assertThatThrownBy(() -> command.accept(2L, "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already accepted");
    }

    @Test
    void privateEventRequiresATargetSeat() {
        assertThatThrownBy(
                        () ->
                                GameEventEntity.seatEvent(
                                        SESSION_ID, 1L, 1, "HAND_DEALT", 0, "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target seat");
    }

    private static long score(GameSessionSeatEntity seat) {
        try {
            return (long) seat.getClass().getMethod("getScore").invoke(seat);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Gameplay seat entity has no authoritative score", error);
        }
    }
}
