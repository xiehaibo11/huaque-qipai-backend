package com.nanbei.entertainment.backend.gameplay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.gameplay.taizhoumahjong.TaizhouMahjongDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameEngineTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void acceptedCommandAdvancesRevisionExactlyOnce() {
        TestState previous = new TestState(GamePhase.WAITING, 7L);
        ReadyCommand command = new ReadyCommand(USER_ID, 7L);
        GameEngine<TestState> engine =
                (state, submitted) -> {
                    submitted.requireRevision(state.revision());
                    TestState next = new TestState(GamePhase.WAITING, state.revision() + 1);
                    GameEvent event =
                            GameEvent.publicEvent(next.revision(), "PLAYER_READY", Map.of());
                    return CommandResult.accepted(state, next, List.of(event));
                };

        CommandResult<TestState> result = engine.handle(previous, command);

        assertThat(result.state().revision()).isEqualTo(8L);
        assertThat(result.events()).extracting(GameEvent::revision).containsExactly(8L);
    }

    @Test
    void staleCommandIsRejectedBeforeStateChanges() {
        ReadyCommand command = new ReadyCommand(USER_ID, 6L);

        assertThatThrownBy(() -> command.requireRevision(7L))
                .isInstanceOf(StaleGameCommandException.class)
                .hasMessageContaining("expected revision 6")
                .hasMessageContaining("current revision 7");
    }

    @Test
    void acceptedResultRejectsAnEventFromAnotherRevision() {
        TestState previous = new TestState(GamePhase.WAITING, 7L);
        TestState next = new TestState(GamePhase.WAITING, 8L);
        GameEvent staleEvent = GameEvent.publicEvent(7L, "PLAYER_READY", Map.of());

        assertThatThrownBy(() -> CommandResult.accepted(previous, next, List.of(staleEvent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event revision");
    }

    @Test
    void autoReadyMarksEveryOccupiedSeatInOneRevisionAndUsesSeatOrder() {
        WaitingRoomState previous =
                new WaitingRoomState(
                        TaizhouMahjongDefinition.GAME_ID,
                        GamePhase.WAITING,
                        4L,
                        Map.of(3, USER_3, 1, USER_ID, 2, USER_2),
                        java.util.Set.of(1));

        CommandResult<WaitingRoomState> result =
                new WaitingRoomEngine()
                        .handle(previous, new AutoReadyCommand(USER_ID, 4L));

        assertThat(result.state().revision()).isEqualTo(5L);
        assertThat(result.state().readySeats()).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(result.events()).hasSize(2);
        assertThat(result.events())
                .extracting(event -> event.payload().get("seatNumber"))
                .containsExactly(2, 3);
        assertThat(result.events())
                .allSatisfy(
                        event -> {
                            assertThat(event.revision()).isEqualTo(5L);
                            assertThat(event.type()).isEqualTo("SEAT_READY_CHANGED");
                            assertThat(event.payload().get("ready")).isEqualTo(true);
                        });
    }

    @Test
    void autoReadyRejectsAnAlreadyConvergedRoom() {
        WaitingRoomState ready =
                new WaitingRoomState(
                        TaizhouMahjongDefinition.GAME_ID,
                        GamePhase.WAITING,
                        4L,
                        Map.of(1, USER_ID, 2, USER_2),
                        java.util.Set.of(1, 2));

        assertThatThrownBy(
                        () ->
                                new WaitingRoomEngine()
                                        .handle(ready, new AutoReadyCommand(USER_ID, 4L)))
                .isInstanceOf(GameActionNotAllowedException.class)
                .hasMessageContaining("准备状态没有变化");
    }

    @Test
    void autoReadyStillRequiresTheCurrentRevision() {
        WaitingRoomState previous =
                new WaitingRoomState(
                        TaizhouMahjongDefinition.GAME_ID,
                        GamePhase.WAITING,
                        4L,
                        Map.of(1, USER_ID, 2, USER_2),
                        java.util.Set.of());

        assertThatThrownBy(
                        () ->
                                new WaitingRoomEngine()
                                        .handle(previous, new AutoReadyCommand(USER_ID, 3L)))
                .isInstanceOf(StaleGameCommandException.class);
    }

    private record TestState(GamePhase phase, long revision) implements GameState {
        @Override
        public long gameId() {
            return TaizhouMahjongDefinition.GAME_ID;
        }
    }
}
