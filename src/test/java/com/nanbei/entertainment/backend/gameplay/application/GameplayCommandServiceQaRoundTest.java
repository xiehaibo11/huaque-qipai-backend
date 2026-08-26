package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameCommandRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameplayCommandServiceQaRoundTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Mock GameRoomRepository roomRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock GameSessionSeatRepository seatRepository;
    @Mock GameCommandRepository commandRepository;
    @Mock GameEventRepository eventRepository;
    @Mock QaGameplayBotService qaBotService;

    ObjectMapper objectMapper;
    GameplayCommandService service;
    GameRoomEntity room;
    GameSessionEntity session;
    GameSessionSeatEntity ownerSeat;
    List<GameSessionSeatEntity> seats;
    List<QaMahjongAutoRoundEngine.SeatInput> seatInputs;
    QaTaizhouRoundResult started;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        service =
                new GameplayCommandService(
                        roomRepository,
                        sessionRepository,
                        seatRepository,
                        commandRepository,
                        eventRepository,
                        new CryptoService(),
                        objectMapper,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        qaBotService);
        room = room();
        session = new GameSessionEntity(room.getId(), 30109L, NOW);
        ownerSeat = new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW);
        ownerSeat.setReady(true, NOW);
        seats = new ArrayList<>();
        seats.add(ownerSeat);
        for (int index = 2; index <= 4; index++) {
            GameSessionSeatEntity bot =
                    new GameSessionSeatEntity(
                            session.getId(),
                            index,
                            UUID.fromString(String.format("20000000-0000-0000-0000-%012d", index)),
                            NOW);
            bot.setReady(true, NOW);
            seats.add(bot);
        }
        seatInputs = QaTaizhouRoundEngineTest.seats(false, true, true, true);
        started =
                new QaTaizhouRoundEngine(objectMapper)
                        .start(
                                new QaTaizhouRoundEngine.Request(
                                        30109L,
                                        "123456",
                                        4,
                                        8,
                                        "不平搓/不封顶",
                                        0L,
                                        0,
                                        seatInputs,
                                        NOW));
        session.advance(
                started.phase(), 1, started.revision(), objectMapper.writeValueAsString(started.state()), NOW);
    }

    @Test
    void aQaSessionAcceptsADiscardCommandWithTheOfferToken() throws Exception {
        QaRoundStep ready = advanceSessionPastMultipleChoice();
        arrangeCommand(OWNER_ID, "discard-1");
        arrangeQaEngine();
        String token = offerToken(ready.events(), 1);
        int tileValue = ready.table().hands().get(1).get(0);

        GameplayCommandResponse response =
                service.submit(
                        OWNER_ID,
                        "123456",
                        "discard-1",
                        new GameplayCommandRequest(
                                GameplayCommandType.DISCARD,
                                2L,
                                objectMapper.readTree(
                                        "{\"tileValue\":"
                                                + tileValue
                                                + ",\"actionToken\":\""
                                                + token
                                                + "\"}")));

        assertThat(response.revision()).isEqualTo(3L);
        assertThat(response.eventType()).isEqualTo("DISCARDED");
        assertThat(response.events())
                .extracting(GameplayEventView::type)
                .containsSubsequence("DISCARDED", "DRAWN", "TURN_ADVANCED", "DISCARDED");
        assertThat(response.events())
                .filteredOn(
                        event ->
                                event.type().equals("DRAWN")
                                        && event.payload().has("publicRound"))
                .hasSize(4);
        assertThat(response.events())
                .filteredOn(event -> event.payload().has("playbackDelayMillis"))
                .hasSize(3)
                .allSatisfy(
                        event ->
                                assertThat(
                                                event.payload()
                                                        .path("playbackDelayMillis")
                                                        .asLong())
                                        .isBetween(
                                                QaBotThinkingRhythm.MIN_MILLIS,
                                                QaBotThinkingRhythm.MAX_MILLIS));
        assertThat(response.events())
                .extracting(GameplayEventView::eventOrder)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, response.events().size())
                                .boxed()
                                .toList());
        assertThat(session.getRevision()).isEqualTo(3L);
        assertThat(objectMapper.readTree(session.getState()).path("remainingWallCount").asInt())
                .isEqualTo(ready.table().wall.size() - 4);
        assertThat(session.getState()).contains("qaDisclosure");

        ArgumentCaptor<GameEventEntity> saved = ArgumentCaptor.forClass(GameEventEntity.class);
        verify(eventRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(GameEventEntity::getRevision)
                .containsOnly(3L);
        assertThat(saved.getAllValues().get(0).getEventType()).isEqualTo("DISCARDED");
        assertThat(saved.getAllValues())
                .filteredOn(event -> event.getVisibility() == GameEvent.Audience.SEAT)
                .allSatisfy(event -> assertThat(event.getTargetSeat()).isNotNull());
    }

    @Test
    void aPlainWaitingSessionRejectsRoundCommandsBeforeStartRound() {
        GameSessionEntity plainSession = new GameSessionEntity(room.getId(), 30109L, NOW);
        when(commandRepository.findByUserIdAndIdempotencyKey(OWNER_ID, "discard-1"))
                .thenReturn(Optional.empty());
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findLockedByRoomId(room.getId()))
                .thenReturn(Optional.of(plainSession));
        when(seatRepository.findByIdSessionIdAndUserId(plainSession.getId(), OWNER_ID))
                .thenReturn(Optional.of(ownerSeat));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(plainSession.getId()))
                .thenReturn(seats);

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID,
                                        "123456",
                                        "discard-1",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.DISCARD,
                                                0L,
                                                objectMapper.readTree(
                                                        "{\"tileValue\":17,\"actionToken\":\"t\"}"))))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aStaleRevisionIsRejectedBeforeTheEngineRuns() {
        arrangeCommand(OWNER_ID, "discard-stale");

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID,
                                        "123456",
                                        "discard-stale",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.DISCARD,
                                                0L,
                                                objectMapper.readTree(
                                                        "{\"tileValue\":17,\"actionToken\":\"t\"}"))))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_COMMAND_STALE);
        assertThat(session.getRevision()).isEqualTo(1L);
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aMultipleChoiceCommandPersistsAPublicChangedEvent() {
        arrangeCommand(OWNER_ID, "choice-1");
        arrangeQaEngine();

        GameplayCommandResponse response =
                service.submit(
                        OWNER_ID,
                        "123456",
                        "choice-1",
                        new GameplayCommandRequest(
                                GameplayCommandType.MULTIPLE_CHOICE,
                                1L,
                                objectMapper.readTree("{\"choice\":\"SUPER\"}")));

        assertThat(response.revision()).isEqualTo(2L);
        assertThat(response.eventType()).isEqualTo("MULTIPLE_CHOICE_CHANGED");
        ArgumentCaptor<GameEventEntity> saved = ArgumentCaptor.forClass(GameEventEntity.class);
        verify(eventRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(GameEventEntity::getEventType)
                .containsSubsequence("MULTIPLE_CHOICE_CHANGED", "DEALT", "ACTION_OFFERED");
        assertThat(saved.getAllValues().get(0).getEventType()).isEqualTo("MULTIPLE_CHOICE_CHANGED");
        assertThat(saved.getAllValues().get(0).getVisibility()).isEqualTo(GameEvent.Audience.PUBLIC);
        assertThat(saved.getAllValues().get(0).getPayload()).contains("SUPER");
    }

    @Test
    void roundCommandsRequireTheQaFlagEvenForQaSessions() {
        arrangeCommand(OWNER_ID, "discard-1");
        when(qaBotService.enabled()).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID,
                                        "123456",
                                        "discard-1",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.DISCARD,
                                                1L,
                                                objectMapper.readTree(
                                                        "{\"tileValue\":17,\"actionToken\":\"t\"}"))))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
    }

    private void arrangeCommand(UUID userId, String key) {
        when(commandRepository.findByUserIdAndIdempotencyKey(userId, key))
                .thenReturn(Optional.empty());
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findLockedByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), userId))
                .thenReturn(Optional.of(ownerSeat));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(seats);
    }

    private void arrangeQaEngine() {
        when(qaBotService.enabled()).thenReturn(true);
        when(qaBotService.seatInputs(room, seats)).thenReturn(seatInputs);
    }

    private QaRoundStep advanceSessionPastMultipleChoice() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(objectMapper);
        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.MULTIPLE_CHOICE,
                        objectMapper.readTree("{\"choice\":\"NONE\"}"),
                        2L);
        JsonNode state = engine.sessionState(step.table(), QaRoundTestRigs.humanDealerContext());
        session.advance(
                QaTaizhouRoundEngine.phaseOf(step.table()),
                1,
                2L,
                objectMapper.writeValueAsString(state),
                NOW);
        return step;
    }

    private static String offerToken(List<GameEvent> events, int seat) {
        for (int index = events.size() - 1; index >= 0; index--) {
            GameEvent event = events.get(index);
            if (event.type().equals("ACTION_OFFERED") && event.targetSeat() == seat) {
                return (String) event.payload().get("actionToken");
            }
        }
        throw new AssertionError("no ACTION_OFFERED for seat " + seat);
    }

    private static GameRoomEntity room() {
        return new GameRoomEntity(
                "123456",
                OWNER_ID,
                900023L,
                30109L,
                "{}",
                "不平搓/不封顶",
                "{}",
                0,
                4,
                8,
                RoomPayType.ALL,
                100,
                "room-key",
                "room-hash");
    }

    private static JsonNode readTree(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
