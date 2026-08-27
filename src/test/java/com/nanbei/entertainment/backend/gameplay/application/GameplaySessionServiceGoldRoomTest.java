package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;

import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
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

/**
 * 金币场（venue=GOLD / roomMode=50）对局快照不依赖房主座位：匹配制场次由 MatchServer 路由、
 * 进 GameM 对局（原版 goldMode==50 实测链路），房主座位仅亲友房/创建房间概念。
 * 房主早退、已转移或旧数据导致 participants 不含 owner 时，进桌与轮询必须照常返回快照。
 */
@ExtendWith(MockitoExtension.class)
class GameplaySessionServiceGoldRoomTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock GameRoomRepository roomRepository;
    @Mock RoomParticipantRepository participantRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock GameSessionSeatRepository seatRepository;
    @Mock UserRepository userRepository;
    @Mock PlayerProfileService profileService;
    @Mock UserEntity guestUser;

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
    void goldRoomSnapshotDoesNotRequireHostSeat() {
        stubGuestProfile();
        GameRoomEntity room = goldRoom(30109L, 4);
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        session.advance(GamePhase.PLAYING, 1, 1L, "{}", NOW);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, GUEST_ID)));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(List.of(new GameSessionSeatEntity(session.getId(), 1, GUEST_ID, NOW)));

        GameplaySnapshot snapshot = service.get(GUEST_ID, "123456");

        assertThat(snapshot.phase()).isEqualTo(GamePhase.PLAYING);
        assertThat(snapshot.mySeat()).isEqualTo(1);
        assertThat(snapshot.seats()).hasSize(1);
    }

    @Test
    void goldRoomOpenWithoutStoredSessionIsRejectedWithoutHostRequirement() {
        // 金币场 session 由匹配流程创建；缺失时直接拒绝进房（原版无「房主创建牌局」语义），
        // 不得退回亲友房的 requireOwner（否则金币场会被当成创建房间/亲友房）。
        GameRoomEntity room = goldRoom(30109L, 4);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(GUEST_ID, "123456"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAMEPLAY_SESSION_NOT_FOUND);
    }

    private static GameRoomEntity goldRoom(long gameId, int playerCount) {
        return new GameRoomEntity(
                "123456",
                OWNER_ID,
                900023L,
                gameId,
                "winLostType='1';playerCount_" + playerCount + ";autoReady='1';GoldMatch='1';",
                "金币场",
                "roomrule={GamePlayerCount=\"\" + playerCount + \"\",roommode=\"50\"}",
                50,
                playerCount,
                1,
                RoomPayType.ALL,
                0,
                "gold-match-key",
                "gold-match:900023:" + gameId + ":1",
                RoomVenue.GOLD);
    }

    private static RoomParticipantEntity participant(GameRoomEntity room, UUID userId) {
        return new RoomParticipantEntity(room.getId(), userId);
    }

    private void stubGuestProfile() {
        when(guestUser.isActive()).thenReturn(true);
        when(guestUser.getDisplayName()).thenReturn("来宾昵称");
        when(userRepository.findById(GUEST_ID)).thenReturn(Optional.of(guestUser));
        when(profileService.ensureProfile(GUEST_ID))
                .thenReturn(
                        new PlayerProfileEntity(
                                GUEST_ID,
                                1084375591L,
                                "avatar_20000000-0000-0000-0000-000000000002",
                                0));
    }
}
