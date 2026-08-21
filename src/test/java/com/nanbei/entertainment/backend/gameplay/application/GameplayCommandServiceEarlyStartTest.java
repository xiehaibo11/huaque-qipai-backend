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
import tools.jackson.databind.ObjectMapper;

/**
 * EARLY_START 服务级契约（南北自建 QA 简化，对齐 msgAdvanceStart 1200-1203 语义但不含
 * 同意/拒绝交互）：等待态房主发起即用 QA 假人补齐空位并启动完整轮转，QA 假人视为同意。
 */
@ExtendWith(MockitoExtension.class)
class GameplayCommandServiceEarlyStartTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
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
    List<GameSessionSeatEntity> waitingSeats;
    List<GameSessionSeatEntity> filledSeats;

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
        waitingSeats = new ArrayList<>();
        waitingSeats.add(ownerSeat);
        filledSeats = new ArrayList<>(waitingSeats);
        for (int index = 2; index <= 4; index++) {
            GameSessionSeatEntity bot =
                    new GameSessionSeatEntity(
                            session.getId(),
                            index,
                            UUID.fromString(String.format("30000000-0000-0000-0000-%012d", index)),
                            NOW);
            bot.setReady(true, NOW);
            filledSeats.add(bot);
        }
    }

    @Test
    void theOwnerEarlyStartsFillsBotSeatsAndOpensTheFullRound() {
        arrangeCommand(OWNER_ID, "early-1", ownerSeat);
        when(qaBotService.enabled()).thenReturn(true);
        when(qaBotService.ensureTenBotsAndFillSeats(room, session, waitingSeats, NOW))
                .thenReturn(filledSeats);
        when(qaBotService.seatInputs(room, filledSeats))
                .thenReturn(QaTaizhouRoundEngineTest.seats(false, true, true, true));

        GameplayCommandResponse response =
                service.submit(
                        OWNER_ID, "123456", "early-1",
                        new GameplayCommandRequest(GameplayCommandType.EARLY_START, 0L, null));

        assertThat(response.revision()).isEqualTo(1L);
        assertThat(response.eventType()).isEqualTo("BOT_SEATS_FILLED");
        assertThat(session.getPhase()).isEqualTo(GamePhase.DEALING);
        assertThat(session.getRoundNumber()).isEqualTo(1);
        assertThat(session.getState()).contains("qaDisclosure");
        assertThat(session.getState()).contains("\"multipleChoice\"");
        verify(qaBotService).ensureTenBotsAndFillSeats(room, session, waitingSeats, NOW);
        ArgumentCaptor<GameEventEntity> saved = ArgumentCaptor.forClass(GameEventEntity.class);
        verify(eventRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(GameEventEntity::getEventType)
                .containsSubsequence(
                        "BOT_SEATS_FILLED",
                        "WALL_SHUFFLED",
                        "MULTIPLE_CHOICE_STARTED")
                .doesNotContain("LEFT_BANKER", "DEALT", "SHENG_PAI_COUNT");
    }

    @Test
    void aNonOwnerCannotEarlyStart() {
        GameSessionSeatEntity guestSeat = new GameSessionSeatEntity(session.getId(), 2, GUEST_ID, NOW);
        arrangeCommand(GUEST_ID, "early-guest", guestSeat);

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        GUEST_ID, "123456", "early-guest",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.EARLY_START, 0L, null)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void earlyStartOutsideTheWaitingPhaseIsRejected() throws Exception {
        QaTaizhouRoundResult started =
                new QaTaizhouRoundEngine(objectMapper)
                        .start(
                                new QaTaizhouRoundEngine.Request(
                                        30109L, "123456", 4, 8, "不平搓/不封顶", 0L, 0,
                                        QaTaizhouRoundEngineTest.seats(false, true, true, true),
                                        NOW));
        session.advance(
                started.phase(), 1, started.revision(),
                objectMapper.writeValueAsString(started.state()), NOW);
        arrangeCommand(OWNER_ID, "early-playing", ownerSeat);

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID, "123456", "early-playing",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.EARLY_START, 1L, null)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
    }

    @Test
    void earlyStartWithoutTheQaFlagIsRejectedAsDebugOnly() {
        arrangeCommand(OWNER_ID, "early-noqa", ownerSeat);
        when(qaBotService.enabled()).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID, "123456", "early-noqa",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.EARLY_START, 0L, null)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void arrangeCommand(UUID userId, String key, GameSessionSeatEntity actorSeat) {
        when(commandRepository.findByUserIdAndIdempotencyKey(userId, key))
                .thenReturn(Optional.empty());
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findLockedByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), userId))
                .thenReturn(Optional.of(actorSeat));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(waitingSeats);
    }

    private static GameRoomEntity room() {
        return new GameRoomEntity(
                "123456", OWNER_ID, 900023L, 30109L, "{}", "不平搓/不封顶", "{}",
                0, 4, 8, RoomPayType.ALL, 100, "room-key", "room-hash");
    }
}
