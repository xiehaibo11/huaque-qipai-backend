package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameplaySessionServiceTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock GameRoomRepository roomRepository;
    @Mock RoomParticipantRepository participantRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock GameSessionSeatRepository seatRepository;
    @Mock UserRepository userRepository;
    @Mock PlayerProfileService profileService;
    @Mock UserEntity ownerUser;
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
    void ownerOpensFull30109RoomWithOwnerInFirstSeat() {
        stubSeatProfiles();
        GameRoomEntity room = room(30109L, 2);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.empty());
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, GUEST_ID), participant(room, OWNER_ID)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GameplaySnapshot snapshot = service.open(OWNER_ID, "123456");

        assertThat(snapshot.roomNumber()).isEqualTo("123456");
        assertThat(snapshot.gameId()).isEqualTo(30109L);
        assertThat(snapshot.phase().name()).isEqualTo("WAITING");
        assertThat(snapshot.revision()).isZero();
        assertThat(snapshot.chairCount()).isEqualTo(2);
        assertThat(snapshot.maxPlayCount()).isEqualTo(8);
        assertThat(snapshot.gameRuleDisplay()).isEqualTo("不平搓/不封顶");
        assertThat(snapshot.mySeat()).isEqualTo(1);
        assertThat(snapshot.seats())
                .extracting(GameplaySeatSnapshot::userId)
                .containsExactly(OWNER_ID, GUEST_ID);
        assertThat(snapshot.seats())
                .extracting(GameplaySessionServiceTest::score)
                .containsExactly(1000L, 1000L);
        assertThat(snapshot.seats().get(0))
                .extracting(
                        GameplaySeatSnapshot::publicPlayerId,
                        GameplaySeatSnapshot::displayName,
                        GameplaySeatSnapshot::avatarKey,
                        GameplaySeatSnapshot::host)
                .containsExactly(1084375590L, "房主昵称", "avatar_default", true);
        assertThat(snapshot.seats().get(1))
                .extracting(
                        GameplaySeatSnapshot::publicPlayerId,
                        GameplaySeatSnapshot::displayName,
                        GameplaySeatSnapshot::avatarKey,
                        GameplaySeatSnapshot::host)
                .containsExactly(
                        1084375591L,
                        "来宾昵称",
                        "avatar_20000000-0000-0000-0000-000000000002",
                        false);

        ArgumentCaptor<List<GameSessionSeatEntity>> seats = ArgumentCaptor.forClass(List.class);
        verify(seatRepository).saveAll(seats.capture());
        assertThat(seats.getValue())
                .extracting(seat -> seat.getId().getSeatNumber())
                .containsExactly(1, 2);
    }

    @Test
    void legacyRoomWithoutStoredDisplayUsesTheTaizhouProtocolRuleText() {
        stubOwnerProfile();
        GameRoomEntity room =
                new GameRoomEntity(
                        "123456",
                        OWNER_ID,
                        900023L,
                        30109L,
                        "winLostType='1';FengDing='0';PayType='0';basescore='1';",
                        "",
                        "{}",
                        0,
                        2,
                        8,
                        RoomPayType.ALL,
                        100,
                        "room-key",
                        "room-hash");
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.empty());
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, OWNER_ID)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GameplaySnapshot snapshot = service.open(OWNER_ID, "123456");

        assertThat(snapshot.gameRuleDisplay())
                .isEqualTo("不平搓/不封顶/房主消耗/2人/底分1/8局");
    }

    @Test
    void snapshotExposesTheOriginalAutoReadyRule() {
        stubOwnerProfile();
        GameRoomEntity room =
                new GameRoomEntity(
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
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.empty());
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, OWNER_ID)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GameplaySnapshot snapshot = service.open(OWNER_ID, "123456");

        assertThat(snapshot.autoReady()).isTrue();
    }

    @Test
    void repeatedOpenReturnsExistingSessionWithoutCreatingAnother() {
        stubSeatProfiles();
        GameRoomEntity room = room(30109L, 2);
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

        GameplaySnapshot snapshot = service.open(OWNER_ID, "123456");

        assertThat(snapshot.sessionId()).isEqualTo(session.getId());
        assertThat(snapshot.seats()).hasSize(2);
        verify(sessionRepository, never()).save(any());
        verify(seatRepository, never()).saveAll(any());
    }

    @Test
    void participantOpensExistingSession() {
        stubSeatProfiles();
        GameRoomEntity room = room(30109L, 2);
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        GameSessionSeatEntity guestSeat =
                new GameSessionSeatEntity(session.getId(), 2, GUEST_ID, NOW);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, OWNER_ID), participant(room, GUEST_ID)));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(
                        List.of(
                                new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW),
                                guestSeat));

        GameplaySnapshot snapshot = service.open(GUEST_ID, "123456");

        assertThat(snapshot.mySeat()).isEqualTo(2);
    }

    @Test
    void outsiderCannotReadExistingSession() {
        GameRoomEntity room = room(30109L, 2);
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, OWNER_ID), participant(room, GUEST_ID)));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(
                        List.of(
                                new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW),
                                new GameSessionSeatEntity(session.getId(), 2, GUEST_ID, NOW)));

        assertThatThrownBy(() -> service.get(OUTSIDER_ID, "123456"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAMEPLAY_FORBIDDEN);
    }

    @Test
    void unsupportedGameCannotOpenGameplaySession() {
        GameRoomEntity room = room(30110L, 2);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.open(OWNER_ID, "123456"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAMEPLAY_NOT_AVAILABLE);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void ownerCanOpenWaitingSessionBeforeRoomIsFull() {
        stubOwnerProfile();
        GameRoomEntity room = room(30109L, 2);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.empty());
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, OWNER_ID)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GameplaySnapshot snapshot = service.open(OWNER_ID, "123456");

        assertThat(snapshot.phase().name()).isEqualTo("WAITING");
        assertThat(snapshot.chairCount()).isEqualTo(2);
        assertThat(snapshot.mySeat()).isEqualTo(1);
        assertThat(snapshot.seats())
                .extracting(GameplaySeatSnapshot::userId)
                .containsExactly(OWNER_ID);
    }

    @Test
    void participantJoiningAfterSessionCreationGetsTheNextStableSeat() {
        stubSeatProfiles();
        GameRoomEntity room = room(30109L, 2);
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        GameSessionSeatEntity ownerSeat =
                new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, OWNER_ID), participant(room, GUEST_ID)));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(List.of(ownerSeat));
        when(seatRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GameplaySnapshot snapshot = service.get(GUEST_ID, "123456");

        assertThat(snapshot.mySeat()).isEqualTo(2);
        assertThat(snapshot.seats())
                .extracting(GameplaySeatSnapshot::userId)
                .containsExactly(OWNER_ID, GUEST_ID);
        ArgumentCaptor<List<GameSessionSeatEntity>> addedSeats =
                ArgumentCaptor.forClass(List.class);
        verify(seatRepository).saveAll(addedSeats.capture());
        assertThat(addedSeats.getValue()).singleElement().satisfies(
                seat -> {
                    assertThat(seat.getId().getSeatNumber()).isEqualTo(2);
                    assertThat(seat.getUserId()).isEqualTo(GUEST_ID);
                });
    }

    @Test
    void dissolvedRoomCannotOpenGameplaySession() {
        GameRoomEntity room = room(30109L, 2);
        room.dissolve(NOW);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.open(OWNER_ID, "123456"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ROOM_ILLEGAL_STATE);
    }

    @Test
    void dissolvedRoomReturnsDissolvedSnapshotForMatchPolling() {
        GameRoomEntity room = room(30109L, 2);
        room.dissolve(NOW);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.empty());
        GameplaySnapshot snapshot = service.get(OWNER_ID, "123456");
        // 安卓匹配轮询靠 phase=DISSOLVED 撤下等待页（原版房散通知），不能抛错。
        assertThat(snapshot.phase()).isEqualTo(GamePhase.DISSOLVED);
        assertThat(snapshot.mySeat()).isEqualTo(1);
        assertThat(snapshot.seats()).isEmpty();
    }

    @Test
    void snapshotCarriesAuthoritativeRoundProjectionAndSettlementFromSessionState() throws Exception {
        stubSeatProfiles();
        GameRoomEntity room = room(30109L, 2);
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        session.advance(
                GamePhase.ROUND_RESULT,
                1,
                1L,
                """
                {
                  "visibleRoundsBySeat": {
                    "1": {
                      "chairCount": 2,
                      "mySeat": 1,
                      "hands": [
                        {"seatNumber": 1, "concealedTiles": [11,12,13], "drawnTile": 14, "meldCount": 0},
                        {"seatNumber": 2, "concealedTiles": [114,114,114], "drawnTile": null, "meldCount": 0}
                      ],
                      "jokerTiles": [31],
                      "insteadTiles": [32],
                      "rivers": [
                        {"seatNumber": 1, "tiles": [21], "maxLineCount": 2},
                        {"seatNumber": 2, "tiles": [], "maxLineCount": 2}
                      ],
                      "lastDiscard": {"seatNumber": 1, "tileIndex": 0}
                    },
                    "2": {
                      "chairCount": 2,
                      "mySeat": 2,
                      "hands": [
                        {"seatNumber": 1, "concealedTiles": [114,114,114], "drawnTile": null, "meldCount": 0},
                        {"seatNumber": 2, "concealedTiles": [21,22,23], "drawnTile": 24, "meldCount": 0}
                      ],
                      "jokerTiles": [31],
                      "insteadTiles": [32],
                      "rivers": [
                        {"seatNumber": 1, "tiles": [21], "maxLineCount": 2},
                        {"seatNumber": 2, "tiles": [], "maxLineCount": 2}
                      ],
                      "lastDiscard": {"seatNumber": 1, "tileIndex": 0}
                    }
                  },
                  "playPermissionsBySeat": {
                    "1": {
                      "actionToken": "token-seat-1",
                      "mode": "SINGLE_CLICK",
                      "playableOriginalIndexes": [1,2,3],
                      "tingOriginalIndexes": [],
                      "actionMaskOriginalIndexes": [],
                      "preBaoOriginalIndexes": []
                    }
                  },
                  "activeSeat": 2,
                  "clockRemainingSeconds": 18,
                  "remainingWallCount": 97,
                  "settlement": {
                    "result": "DIANPAO",
                    "roomNumber": "123456",
                    "roundLabel": "第1局",
                    "time": "2026-08-12 23:04:09",
                    "gameRule": "不平搓/不封顶",
                    "seats": [
                      {"seatNumber": 1, "displayName": "房主昵称", "publicPlayerId": "1084375590", "wind": 0, "banker": false, "handHu": 18, "tai": 0, "totalHu": 18, "playerState": 1, "fan": 18, "gangScore": 0, "total": 4000, "delta": 4000, "hasCaishen": true, "handTiles": [11,12,13]},
                      {"seatNumber": 2, "displayName": "来宾昵称", "publicPlayerId": "1084375591", "wind": 1, "banker": false, "handHu": 4, "tai": 0, "totalHu": 4, "playerState": 0, "fan": 4, "gangScore": 0, "total": -700, "delta": -700, "hasCaishen": false, "handTiles": [21,22,23]}
                    ]
                  }
                }
                """,
                NOW);
        when(roomRepository.findLockedByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(participant(room, OWNER_ID), participant(room, GUEST_ID)));
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(
                        List.of(
                                new GameSessionSeatEntity(session.getId(), 1, OWNER_ID, NOW),
                                new GameSessionSeatEntity(session.getId(), 2, GUEST_ID, NOW)));

        GameplaySnapshot snapshot = service.get(OWNER_ID, "123456");

        assertThat(snapshot.phase()).isEqualTo(GamePhase.ROUND_RESULT);
        assertThat(snapshot.activeSeat()).isEqualTo(2);
        assertThat(snapshot.clockRemainingSeconds()).isEqualTo(18);
        assertThat(snapshot.remainingWallCount()).isEqualTo(97);
        assertThat(snapshot.visibleRound()).isNotNull();
        assertThat(snapshot.visibleRound().path("hands").get(0).path("concealedTiles").get(0).asInt())
                .isEqualTo(11);
        assertThat(snapshot.playPermission()).isNotNull();
        assertThat(snapshot.playPermission().path("actionToken").asText()).isEqualTo("token-seat-1");
        assertThat(snapshot.settlement()).isNotNull();
        assertThat(snapshot.settlement().path("result").asText()).isEqualTo("DIANPAO");
        assertThat(snapshot.settlement().path("seats").get(0).path("delta").asLong())
                .isEqualTo(4000L);

        GameplaySnapshot guestSnapshot = service.get(GUEST_ID, "123456");
        assertThat(guestSnapshot.mySeat()).isEqualTo(2);
        assertThat(guestSnapshot.visibleRound().path("mySeat").asInt()).isEqualTo(2);
        assertThat(guestSnapshot.visibleRound().path("hands").get(1).path("concealedTiles").get(0).asInt())
                .isEqualTo(21);
        assertThat(guestSnapshot.playPermission()).isNull();
    }

    private static GameRoomEntity room(long gameId, int playerCount) {
        return new GameRoomEntity(
                "123456",
                OWNER_ID,
                900023L,
                gameId,
                "{}",
                "不平搓/不封顶",
                "{}",
                0,
                playerCount,
                8,
                RoomPayType.ALL,
                100,
                "room-key",
                "room-hash");
    }

    private static long score(GameplaySeatSnapshot seat) {
        try {
            return (long) seat.getClass().getMethod("score").invoke(seat);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Gameplay seat snapshot has no authoritative score", error);
        }
    }

    private static RoomParticipantEntity participant(GameRoomEntity room, UUID userId) {
        return new RoomParticipantEntity(room.getId(), userId);
    }

    private void stubSeatProfiles() {
        stubOwnerProfile();
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

    private void stubOwnerProfile() {
        when(ownerUser.isActive()).thenReturn(true);
        when(ownerUser.getDisplayName()).thenReturn("房主昵称");
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(ownerUser));
        when(profileService.ensureProfile(OWNER_ID))
                .thenReturn(
                        new PlayerProfileEntity(
                                OWNER_ID, 1084375590L, "avatar_default", 0));
    }
}
