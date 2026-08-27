package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
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
class GameplaySessionGoldMatchServiceTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SYNTHETIC_OWNER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock GameRoomRepository roomRepository;
    @Mock RoomParticipantRepository participantRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock GameSessionSeatRepository seatRepository;
    @Mock UserRepository userRepository;
    @Mock PlayerProfileService profileService;

    GameplaySessionService service;

    @BeforeEach
    void setUp() {
        service =
                new GameplaySessionService(
                        roomRepository,
                        participantRepository,
                        sessionRepository,
                        seatRepository,
                        userRepository,
                        profileService,
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void goldSessionSnapshotUsesMatchedSeatsWhenRoomOwnerIsNotAPlayer() {
        stubProfile(OWNER_ID, "玩家A", 1084375590L);
        stubProfile(GUEST_ID, "玩家B", 1084375591L);
        GameRoomEntity room = goldRoom(SYNTHETIC_OWNER_ID, 2);
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        List<GameSessionSeatEntity> seats =
                List.of(
                        new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW),
                        new GameSessionSeatEntity(session.getId(), 2, GUEST_ID, NOW));
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, OWNER_ID), participant(room, GUEST_ID)));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(seats);

        GameplaySnapshot snapshot = service.get(OWNER_ID, "123456");

        assertThat(snapshot.roomVenue()).isEqualTo(RoomVenue.GOLD.name());
        assertThat(snapshot.roomMode()).isEqualTo(50);
        assertThat(snapshot.mySeat()).isEqualTo(1);
        assertThat(snapshot.seats())
                .extracting(GameplaySeatSnapshot::userId)
                .containsExactly(OWNER_ID, GUEST_ID);
        assertThat(snapshot.seats())
                .extracting(GameplaySeatSnapshot::host)
                .containsExactly(false, false);
    }

    @Test
    void goldRoomWithoutMatchSessionDoesNotUseOwnerCreatePath() {
        GameRoomEntity room = goldRoom(SYNTHETIC_OWNER_ID, 2);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(OWNER_ID, "123456"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(ErrorCode.GAMEPLAY_SESSION_NOT_FOUND);
                            assertThat(exception.getMessage()).isEqualTo("金币场牌局尚未创建");
                        });
        verify(sessionRepository, never()).save(any());
        verify(seatRepository, never()).saveAll(any());
    }

    private void stubProfile(UUID userId, String displayName, long publicPlayerId) {
        UserEntity user = UserEntity.create(displayName);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(profileService.ensureProfile(userId))
                .thenReturn(new PlayerProfileEntity(userId, publicPlayerId, "avatar_default", 0));
    }

    private static RoomParticipantEntity participant(GameRoomEntity room, UUID userId) {
        return new RoomParticipantEntity(room.getId(), userId);
    }

    private static GameRoomEntity goldRoom(UUID ownerId, int playerCount) {
        return new GameRoomEntity(
                "123456",
                ownerId,
                900023L,
                30109L,
                "GoldMatch='1';autoReady='1';",
                "金币场",
                "roomrule={roommode=\"50\"}",
                50,
                playerCount,
                1,
                RoomPayType.ALL,
                0,
                "gold-room-key",
                "gold-match:900023:30109:1",
                RoomVenue.GOLD);
    }
}
