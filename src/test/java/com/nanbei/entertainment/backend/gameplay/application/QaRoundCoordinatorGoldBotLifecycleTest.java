package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class QaRoundCoordinatorGoldBotLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void nextRoundUsesTheReplacementQaGoldBotAtTheSameSeat() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<QaMahjongAutoRoundEngine.SeatInput> oldInputs =
                QaTaizhouRoundEngineTest.seats(true, true, true, true);
        QaTaizhouRoundResult finished =
                new QaTaizhouRoundEngine(objectMapper).start(
                        QaTaizhouRoundEngineTest.request(oldInputs));
        GameRoomEntity room = room(oldInputs.getFirst().userId());
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        session.advance(
                GamePhase.ROUND_RESULT,
                1,
                1L,
                objectMapper.writeValueAsString(finished.state()),
                NOW);
        List<GameSessionSeatEntity> oldSeats = seats(session, oldInputs);
        UUID replacementId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        List<GameSessionSeatEntity> replacementSeats = new ArrayList<>(oldSeats);
        replacementSeats.set(
                1,
                new GameSessionSeatEntity(session.getId(), 2, replacementId, 30_000L, NOW));
        List<QaMahjongAutoRoundEngine.SeatInput> replacementInputs =
                new ArrayList<>(oldInputs);
        replacementInputs.set(
                1,
                new QaMahjongAutoRoundEngine.SeatInput(
                        2, replacementId, "晚风", 2084375591L, 30_000L, true));
        QaGameplayBotService bots = Mockito.mock(QaGameplayBotService.class);
        when(bots.enabled()).thenReturn(true);
        when(bots.replaceIneligibleGoldBots(room, session, oldSeats, NOW))
                .thenReturn(replacementSeats);
        when(bots.seatInputs(room, oldSeats)).thenReturn(oldInputs);
        when(bots.seatInputs(room, replacementSeats)).thenReturn(replacementInputs);

        QaRoundCoordinator.QaRoundCommandOutcome outcome =
                new QaRoundCoordinator(bots, objectMapper)
                        .applyCommand(
                                room,
                                session,
                                oldSeats.getFirst(),
                                oldSeats,
                                GameplayCommandType.NEXT_ROUND,
                                null,
                                NOW);

        GameEvent filled =
                outcome.events().stream()
                        .filter(event -> event.type().equals("BOT_SEATS_FILLED"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> visibleSeats =
                (List<Map<String, Object>>) filled.payload().get("seats");
        assertThat(visibleSeats.get(1))
                .containsEntry("userId", replacementId.toString())
                .containsEntry("displayName", "晚风");
        assertThat(outcome.seats()).containsExactlyElementsOf(replacementSeats);
    }

    private static List<GameSessionSeatEntity> seats(
            GameSessionEntity session, List<QaMahjongAutoRoundEngine.SeatInput> inputs) {
        return inputs.stream()
                .map(
                        input ->
                                new GameSessionSeatEntity(
                                        session.getId(),
                                        input.seatNumber(),
                                        input.userId(),
                                        input.score(),
                                        NOW))
                .toList();
    }

    private static GameRoomEntity room(UUID ownerId) {
        return new GameRoomEntity(
                "123456",
                ownerId,
                900023L,
                30109L,
                "QaGoldMatch='1';QaBotMinCoins='30000';basescore='600';",
                "不平搓/底分600/8圈",
                "{}",
                50,
                4,
                8,
                RoomPayType.ALL,
                0,
                "request-key",
                "request-hash",
                RoomVenue.GOLD);
    }
}
