package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameplayCommandRoundResultContractTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock GameRoomRepository roomRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock GameSessionSeatRepository seatRepository;
    @Mock GameCommandRepository commandRepository;
    @Mock GameEventRepository eventRepository;
    @Mock QaGameplayBotService qaBotService;

    GameplayCommandService service;
    GameRoomEntity room;
    GameSessionEntity session;
    GameSessionSeatEntity ownerSeat;
    GameSessionSeatEntity guestSeat;

    @BeforeEach
    void setUp() {
        service =
                new GameplayCommandService(
                        roomRepository,
                        sessionRepository,
                        seatRepository,
                        commandRepository,
                        eventRepository,
                        new CryptoService(),
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        qaBotService);
        room = room();
        session = new GameSessionEntity(room.getId(), 30109L, NOW);
        ownerSeat = new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW);
        guestSeat = new GameSessionSeatEntity(session.getId(), 2, GUEST_ID, NOW);
    }

    @Test
    void startRoundPersistsSeatScopedDealEventsWithoutQaDisclosure() {
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
        assertThat(session.getPhase()).isEqualTo(GamePhase.DEALING);
        assertThat(session.getState()).contains("\"serverAuthority\":true");
        assertThat(session.getState()).contains("\"multipleChoice\"");
        assertThat(session.getState()).doesNotContain("qaDisclosure");
        verify(commandRepository).save(any());
        org.mockito.ArgumentCaptor<com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity>
                events =
                        org.mockito.ArgumentCaptor.forClass(
                                com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity.class);
        verify(eventRepository, org.mockito.Mockito.atLeastOnce()).save(events.capture());
        assertThat(events.getAllValues())
                .extracting(event -> event.getEventType())
                .containsSubsequence("WALL_SHUFFLED", "MULTIPLE_CHOICE_STARTED")
                .doesNotContain("DEALT");
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
}
