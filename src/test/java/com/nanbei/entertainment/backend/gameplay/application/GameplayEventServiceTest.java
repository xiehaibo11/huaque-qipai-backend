package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameplayEventServiceTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock GameRoomRepository roomRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock GameSessionSeatRepository seatRepository;
    @Mock GameEventRepository eventRepository;

    GameplayEventService service;
    GameRoomEntity room;
    GameSessionEntity session;

    @BeforeEach
    void setUp() {
        service =
                new GameplayEventService(
                        roomRepository,
                        sessionRepository,
                        seatRepository,
                        eventRepository,
                        new ObjectMapper());
        room = room();
        session = new GameSessionEntity(room.getId(), 30109L, NOW);
    }

    @Test
    void recoveryReturnsPublicAndOwnPrivateEventsOnly() {
        GameSessionSeatEntity ownerSeat =
                new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW);
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), OWNER_ID))
                .thenReturn(Optional.of(ownerSeat));
        when(eventRepository.findBySessionIdAndRevisionGreaterThanOrderByRevisionAscEventOrderAsc(
                        session.getId(), 0L))
                .thenReturn(
                        List.of(
                                GameEventEntity.publicEvent(
                                        session.getId(),
                                        1L,
                                        1,
                                        "ROUND_STARTED",
                                        "{\"dealerSeat\":1}",
                                        NOW),
                                GameEventEntity.seatEvent(
                                        session.getId(),
                                        1L,
                                        2,
                                        "HAND_DEALT",
                                        1,
                                        "{\"tiles\":[17,18]}",
                                        NOW),
                                GameEventEntity.seatEvent(
                                        session.getId(),
                                        1L,
                                        3,
                                        "HAND_DEALT",
                                        2,
                                        "{\"tiles\":[33,34]}",
                                        NOW)));

        List<GameplayEventView> events = service.after(OWNER_ID, "123456", 0L);

        assertThat(events).extracting(GameplayEventView::eventOrder).containsExactly(1, 2);
        assertThat(events).extracting(GameplayEventView::type)
                .containsExactly("ROUND_STARTED", "HAND_DEALT");
        assertThat(events.get(1).payload().path("tiles").get(0).asInt()).isEqualTo(17);
        assertThat(events.get(1).payload().path("tiles").get(1).asInt()).isEqualTo(18);
    }

    @Test
    void recoveryRenumbersVisibleEventsWhenAnotherSeatEventIsFilteredOut() {
        GameSessionSeatEntity ownerSeat =
                new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW);
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), OWNER_ID))
                .thenReturn(Optional.of(ownerSeat));
        when(eventRepository.findBySessionIdAndRevisionGreaterThanOrderByRevisionAscEventOrderAsc(
                        session.getId(), 0L))
                .thenReturn(
                        List.of(
                                GameEventEntity.publicEvent(
                                        session.getId(), 1L, 1, "DEALT", "{}", NOW),
                                GameEventEntity.seatEvent(
                                        session.getId(), 1L, 2, "DEALT", 1, "{}", NOW),
                                GameEventEntity.seatEvent(
                                        session.getId(), 1L, 3, "DEALT", 2, "{}", NOW),
                                GameEventEntity.publicEvent(
                                        session.getId(), 1L, 4, "DRAWN", "{}", NOW)));

        List<GameplayEventView> events = service.after(OWNER_ID, "123456", 0L);

        assertThat(events).extracting(GameplayEventView::eventOrder).containsExactly(1, 2, 3);
        assertThat(events).extracting(GameplayEventView::type)
                .containsExactly("DEALT", "DEALT", "DRAWN");
    }

    @Test
    void outsiderCannotRecoverEvents() {
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), GUEST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.after(GUEST_ID, "123456", 0L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAMEPLAY_FORBIDDEN);
    }

    @Test
    void negativeRecoveryRevisionIsRejected() {
        assertThatThrownBy(() -> service.after(OWNER_ID, "123456", -1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
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
}
