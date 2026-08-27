package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameCommandEntity;
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

@ExtendWith(MockitoExtension.class)
class GameplayCommandServiceTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

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
    GameSessionSeatEntity guestSeat;

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
        guestSeat = new GameSessionSeatEntity(session.getId(), 2, GUEST_ID, NOW);
    }

    @Test
    void readyAdvancesRevisionAndPersistsOnePublicEvent() {
        arrangeCommand(OWNER_ID, "ready-1");

        GameplayCommandResponse response =
                service.submit(
                        OWNER_ID,
                        "123456",
                        "ready-1",
                        new GameplayCommandRequest(GameplayCommandType.READY, 0L));

        assertThat(response)
                .isEqualTo(
                        new GameplayCommandResponse(
                                1L, "SEAT_READY_CHANGED", 1, true, false));
        assertThat(ownerSeat.isReady()).isTrue();
        assertThat(session.getRevision()).isEqualTo(1L);
        assertThat(session.getPhase()).isEqualTo(GamePhase.WAITING);

        ArgumentCaptor<GameEventEntity> event = ArgumentCaptor.forClass(GameEventEntity.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getRevision()).isEqualTo(1L);
        assertThat(event.getValue().getEventType()).isEqualTo("SEAT_READY_CHANGED");
        assertThat(event.getValue().getVisibility().name()).isEqualTo("PUBLIC");

        ArgumentCaptor<GameCommandEntity> command =
                ArgumentCaptor.forClass(GameCommandEntity.class);
        verify(commandRepository).save(command.capture());
        assertThat(command.getValue().getAcceptedRevision()).isEqualTo(1L);
    }

    @Test
    void readyConvergesEveryOccupiedSeatForAnOriginalAutoReadyRoom() {
        room = autoReadyRoom();
        arrangeCommand(OWNER_ID, "ready-auto");

        GameplayCommandResponse response =
                service.submit(
                        OWNER_ID,
                        "123456",
                        "ready-auto",
                        new GameplayCommandRequest(GameplayCommandType.READY, 0L));

        assertThat(response.revision()).isEqualTo(1L);
        assertThat(ownerSeat.isReady()).isTrue();
        assertThat(guestSeat.isReady()).isTrue();
        assertThat(session.getRevision()).isEqualTo(1L);

        ArgumentCaptor<GameEventEntity> events =
                ArgumentCaptor.forClass(GameEventEntity.class);
        verify(eventRepository, times(2)).save(events.capture());
        assertThat(events.getAllValues())
                .extracting(GameEventEntity::getEventOrder)
                .containsExactly(1, 2);
        assertThat(events.getAllValues())
                .extracting(GameEventEntity::getRevision)
                .containsOnly(1L);
    }

    @Test
    void unreadyUsesTheNextRevision() {
        ownerSeat.setReady(true, NOW);
        session.advance(GamePhase.WAITING, 0, 1L, "{}", NOW);
        arrangeCommand(OWNER_ID, "unready-1");

        GameplayCommandResponse response =
                service.submit(
                        OWNER_ID,
                        "123456",
                        "unready-1",
                        new GameplayCommandRequest(GameplayCommandType.UNREADY, 1L));

        assertThat(response.revision()).isEqualTo(2L);
        assertThat(response.ready()).isFalse();
        assertThat(ownerSeat.isReady()).isFalse();
    }

    @Test
    void sameKeyAndBodyReplaysStoredResponse() throws Exception {
        GameplayCommandRequest request =
                new GameplayCommandRequest(GameplayCommandType.READY, 0L);
        String hash = new CryptoService().sha256(request.canonicalValue("123456"));
        GameCommandEntity existing =
                new GameCommandEntity(
                        session.getId(),
                        OWNER_ID,
                        "ready-1",
                        hash,
                        "READY",
                        0L,
                        NOW);
        existing.accept(
                1L,
                objectMapper.writeValueAsString(
                        new GameplayCommandResponse(
                                1L, "SEAT_READY_CHANGED", 1, true, false)));
        when(commandRepository.findByUserIdAndIdempotencyKey(OWNER_ID, "ready-1"))
                .thenReturn(Optional.of(existing));

        GameplayCommandResponse response =
                service.submit(OWNER_ID, "123456", "ready-1", request);

        assertThat(response.replayed()).isTrue();
        assertThat(response.revision()).isEqualTo(1L);
        verify(sessionRepository, never()).findLockedByRoomId(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        GameCommandEntity existing =
                new GameCommandEntity(
                        session.getId(),
                        OWNER_ID,
                        "ready-1",
                        "different-hash",
                        "READY",
                        0L,
                        NOW);
        when(commandRepository.findByUserIdAndIdempotencyKey(OWNER_ID, "ready-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID,
                                        "123456",
                                        "ready-1",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.READY, 0L)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_IDEMPOTENCY_CONFLICT);
    }

    @Test
    void staleRevisionDoesNotMutateOrPersist() {
        session.advance(GamePhase.WAITING, 0, 1L, "{}", NOW);
        arrangeCommand(OWNER_ID, "ready-1");

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID,
                                        "123456",
                                        "ready-1",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.READY, 0L)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_COMMAND_STALE);
        assertThat(ownerSeat.isReady()).isFalse();
        assertThat(session.getRevision()).isEqualTo(1L);
        verify(commandRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void nonMemberCannotSubmitCommand() {
        UUID outsiderId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        when(commandRepository.findByUserIdAndIdempotencyKey(outsiderId, "ready-1"))
                .thenReturn(Optional.empty());
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findLockedByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), outsiderId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        outsiderId,
                                        "123456",
                                        "ready-1",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.READY, 0L)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAMEPLAY_FORBIDDEN);
    }

    @Test
    void repeatingCurrentReadyStateIsNotAnAcceptedCommand() {
        ownerSeat.setReady(true, NOW);
        arrangeCommand(OWNER_ID, "ready-1");

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID,
                                        "123456",
                                        "ready-1",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.READY, 0L)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
        assertThat(session.getRevision()).isZero();
        verify(eventRepository, never()).save(any());
    }

    @Test
    void trustPublishesOriginalTrustEventWithoutChangingTableState() {
        room = trustRoom();
        session.advance(GamePhase.PLAYING, 1, 1L, "{\"round\":1}", NOW);
        arrangeCommand(OWNER_ID, "trust-1");

        GameplayCommandResponse response =
                service.submit(
                        OWNER_ID,
                        "123456",
                        "trust-1",
                        new GameplayCommandRequest(
                                GameplayCommandType.TRUST,
                                1L,
                                objectMapper.createObjectNode().put("trusted", true)));

        assertThat(response)
                .isEqualTo(new GameplayCommandResponse(2L, "TRUST", 1, false, false));
        assertThat(session.getRevision()).isEqualTo(2L);
        assertThat(session.getPhase()).isEqualTo(GamePhase.PLAYING);
        assertThat(session.getState()).isEqualTo("{\"round\":1}");

        ArgumentCaptor<GameEventEntity> event = ArgumentCaptor.forClass(GameEventEntity.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getRevision()).isEqualTo(2L);
        assertThat(event.getValue().getEventType()).isEqualTo("TRUST");
        assertThat(event.getValue().getVisibility().name()).isEqualTo("PUBLIC");
        assertThat(event.getValue().getPayload())
                .isEqualTo("{\"seat\":1,\"trusted\":true,\"punishSeconds\":15}");
    }

    @Test
    void startRoundStartsAServerAuthoritativeRoundForAFullReadyRoom() {
        ownerSeat.setReady(true, NOW);
        guestSeat.setReady(true, NOW);
        arrangeCommand(OWNER_ID, "start-1");
        when(qaBotService.seatInputs(room, List.of(ownerSeat, guestSeat)))
                .thenReturn(
                        List.of(
                                new QaMahjongAutoRoundEngine.SeatInput(
                                        1, OWNER_ID, "房主", 900001L, 1000L, false),
                                new QaMahjongAutoRoundEngine.SeatInput(
                                        2, GUEST_ID, "玩家2", 900002L, 1000L, false)));

        GameplayCommandResponse response =
                service.submit(
                        OWNER_ID,
                        "123456",
                        "start-1",
                        new GameplayCommandRequest(GameplayCommandType.START_ROUND, 0L));

        assertThat(response.revision()).isEqualTo(1L);
        assertThat(response.eventType()).isEqualTo("WALL_SHUFFLED");
        assertThat(session.getRevision()).isEqualTo(1L);
        assertThat(session.getPhase()).isEqualTo(GamePhase.DEALING);
        assertThat(session.getState()).contains("\"engineMode\":\"SERVER_AUTHORITY\"");
        assertThat(session.getState()).contains("\"multipleChoice\"");
        assertThat(session.getState()).doesNotContain("qaDisclosure");

        ArgumentCaptor<GameEventEntity> events = ArgumentCaptor.forClass(GameEventEntity.class);
        verify(eventRepository, org.mockito.Mockito.atLeastOnce()).save(events.capture());
        assertThat(events.getAllValues())
                .extracting(GameEventEntity::getEventType)
                .containsSubsequence("WALL_SHUFFLED", "MULTIPLE_CHOICE_STARTED")
                .doesNotContain("DEALT", "ACTION_OFFERED");
    }

    @Test
    void idempotencyKeyIsRequired() {
        assertThatThrownBy(
                        () ->
                                service.submit(
                                        OWNER_ID,
                                        "123456",
                                        " ",
                                        new GameplayCommandRequest(
                                                GameplayCommandType.READY, 0L)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(commandRepository, never()).acquireCommandLock(anyString());
    }

    private void arrangeCommand(UUID userId, String key) {
        when(commandRepository.findByUserIdAndIdempotencyKey(userId, key))
                .thenReturn(Optional.empty());
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findLockedByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), userId))
                .thenReturn(Optional.of(ownerSeat));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(List.of(ownerSeat, guestSeat));
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
                2,
                8,
                RoomPayType.ALL,
                100,
                "room-key",
                "room-hash");
    }

    private static GameRoomEntity autoReadyRoom() {
        return new GameRoomEntity(
                "123456",
                OWNER_ID,
                900023L,
                30109L,
                "autoReady='1';",
                "不平搓/自动准备/不封顶",
                "{}",
                0,
                2,
                8,
                RoomPayType.ALL,
                100,
                "room-key",
                "room-hash");
    }

    private static GameRoomEntity trustRoom() {
        return new GameRoomEntity(
                "123456",
                OWNER_ID,
                900023L,
                30109L,
                "IsSysTrust='15';",
                "超时15秒托管",
                "{}",
                0,
                2,
                8,
                RoomPayType.ALL,
                100,
                "room-key",
                "room-hash");
    }
}
