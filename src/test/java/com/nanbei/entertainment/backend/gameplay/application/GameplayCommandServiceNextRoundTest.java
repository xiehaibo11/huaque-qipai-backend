package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
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

/** NEXT_ROUND 服务级契约（南北自建 QA 多局流转）：局数推进、比分累积、局尽完结与非 QA 门禁。 */
@ExtendWith(MockitoExtension.class)
class GameplayCommandServiceNextRoundTest {
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

    @BeforeEach
    void setUp() {
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
            seats.add(
                    new GameSessionSeatEntity(
                            session.getId(),
                            index,
                            UUID.fromString(String.format("20000000-0000-0000-0000-%012d", index)),
                            NOW));
        }
    }

    @Test
    void nextRoundAdvancesTheRoundNumberAndAccumulatesScoresAcrossRounds() throws Exception {
        advanceSessionToFinishedRound(1);
        arrangeQaRound();

        GameplayCommandResponse roundTwo =
                service.submit(
                        OWNER_ID, "123456", "next-round-1",
                        new GameplayCommandRequest(GameplayCommandType.NEXT_ROUND, 1L, null));

        assertThat(roundTwo.revision()).isEqualTo(2L);
        assertThat(session.getRoundNumber()).isEqualTo(2);
        assertThat(session.getPhase()).isEqualTo(GamePhase.ROUND_RESULT);
        JsonNode roundTwoSettlement = objectMapper.readTree(session.getState()).path("settlement");
        List<Long> afterRoundTwo = seatScores();
        for (int index = 0; index < 4; index++) {
            assertThat(afterRoundTwo.get(index) - 1000L)
                    .isEqualTo(roundTwoSettlement.path("seats").get(index).path("delta").asLong());
        }

        arrangeQaRound();
        GameplayCommandResponse roundThree =
                service.submit(
                        OWNER_ID, "123456", "next-round-2",
                        new GameplayCommandRequest(GameplayCommandType.NEXT_ROUND, 2L, null));

        assertThat(roundThree.revision()).isEqualTo(3L);
        assertThat(session.getRoundNumber()).isEqualTo(3);
        JsonNode settlement = objectMapper.readTree(session.getState()).path("settlement");
        List<Long> afterRoundThree = seatScores();
        for (int index = 0; index < 4; index++) {
            long roundThreeDelta =
                    settlement.path("seats").get(index).path("delta").asLong();
            assertThat(afterRoundThree.get(index) - afterRoundTwo.get(index))
                    .isEqualTo(roundThreeDelta);
        }
        assertThat(afterRoundThree.stream().mapToLong(Long::longValue).sum()).isEqualTo(4000L);
    }

    @Test
    void nextRoundBeyondMaxPlayCountCompletesTheSessionAndIsRejected() throws Exception {
        advanceSessionToFinishedRound(8);
        when(commandRepository.findByUserIdAndIdempotencyKey(OWNER_ID, "next-round-x"))
                .thenReturn(Optional.empty());
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findLockedByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), OWNER_ID))
                .thenReturn(Optional.of(ownerSeat));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(seats);
        when(qaBotService.enabled()).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID, "123456", "next-round-x",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.NEXT_ROUND, 1L, null)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
        assertThat(session.getPhase()).isEqualTo(GamePhase.COMPLETED);
        assertThat(session.getRevision()).isEqualTo(1L);
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nextRoundBeforeAnyStartedRoundIsRejectedAsOutOfPhase() {
        when(commandRepository.findByUserIdAndIdempotencyKey(OWNER_ID, "next-round-1"))
                .thenReturn(Optional.empty());
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findLockedByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), OWNER_ID))
                .thenReturn(Optional.of(ownerSeat));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(seats);

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID, "123456", "next-round-1",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.NEXT_ROUND, 0L, null)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
    }

    private void advanceSessionToFinishedRound(int roundNumber) throws Exception {
        QaTaizhouRoundResult finished =
                new QaTaizhouRoundEngine(objectMapper)
                        .start(
                                new QaTaizhouRoundEngine.Request(
                                        30109L, "123456", 4, 8, "不平搓/不封顶",
                                        0L, roundNumber - 1,
                                        QaTaizhouRoundEngineTest.seats(true, true, true, true),
                                        NOW));
        session.advance(
                GamePhase.ROUND_RESULT,
                roundNumber,
                finished.revision(),
                objectMapper.writeValueAsString(finished.state()),
                NOW);
    }

    private void arrangeQaRound() {
        when(commandRepository.findByUserIdAndIdempotencyKey(
                        org.mockito.ArgumentMatchers.eq(OWNER_ID),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findLockedByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), OWNER_ID))
                .thenReturn(Optional.of(ownerSeat));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(seats);
        when(qaBotService.enabled()).thenReturn(true);
        when(qaBotService.seatInputs(room, seats))
                .thenReturn(QaTaizhouRoundEngineTest.seats(true, true, true, true));
    }

    private List<Long> seatScores() {
        return seats.stream().map(GameSessionSeatEntity::getScore).toList();
    }

    private static GameRoomEntity room() {
        return new GameRoomEntity(
                "123456", OWNER_ID, 900023L, 30109L, "{}", "不平搓/不封顶", "{}",
                0, 4, 8, RoomPayType.ALL, 100, "room-key", "room-hash");
    }
}
