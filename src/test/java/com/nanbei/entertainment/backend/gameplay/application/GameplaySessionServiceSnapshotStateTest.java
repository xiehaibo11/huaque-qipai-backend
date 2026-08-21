package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/**
 * 快照 state 字段映射：QA 会话的 {@code actionOffersBySeat/melds/flowers} 经
 * {@link GameplaySessionService} 裁剪为 {@code actionOffer/melds/flowers}，
 * 供 Android 轮询整体替换时恢复动作条、副露与花牌区。
 */
@ExtendWith(MockitoExtension.class)
class GameplaySessionServiceSnapshotStateTest {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
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
    void snapshotCarriesActionOfferMeldsAndFlowersFromSessionState() {
        stubSeatProfiles();
        GameRoomEntity room = room();
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        session.advance(
                GamePhase.PLAYING,
                1,
                1L,
                """
                {
                  "actionOffersBySeat": {
                    "2": {
                      "seat": 2,
                      "powerMask": 24,
                      "actionToken": "offer-token-2",
                      "contextTile": 33,
                      "chowCandidates": [[33, 34, 35]],
                      "kongOptions": [{"kongType": "EXPOSED", "tileValue": 33}],
                      "offerId": 9
                    }
                  },
                  "melds": [
                    {"seat": 1, "combType": "PONG", "tiles": [21, 21, 21], "fromSeat": 2}
                  ],
                  "flowers": [
                    {"seat": 2, "tiles": [97, 98]}
                  ]
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

        GameplaySnapshot ownerSnapshot = service.get(OWNER_ID, "123456");
        assertThat(ownerSnapshot.actionOffer()).isNull();
        assertThat(ownerSnapshot.melds()).isNotNull();
        assertThat(ownerSnapshot.melds().get(0).path("seat").asInt()).isEqualTo(1);
        assertThat(ownerSnapshot.melds().get(0).path("combType").asText()).isEqualTo("PONG");
        assertThat(ownerSnapshot.flowers()).isNotNull();
        assertThat(ownerSnapshot.flowers().get(0).path("tiles").get(1).asInt()).isEqualTo(98);

        GameplaySnapshot guestSnapshot = service.get(GUEST_ID, "123456");
        assertThat(guestSnapshot.actionOffer()).isNotNull();
        assertThat(guestSnapshot.actionOffer().path("actionToken").asText())
                .isEqualTo("offer-token-2");
        assertThat(guestSnapshot.actionOffer().path("offerId").asInt()).isEqualTo(9);
        assertThat(
                        guestSnapshot
                                .actionOffer()
                                .path("kongOptions")
                                .get(0)
                                .path("kongType")
                                .asText())
                .isEqualTo("EXPOSED");
        assertThat(guestSnapshot.melds()).isNotNull();
        assertThat(guestSnapshot.flowers()).isNotNull();
    }

    @Test
    void snapshotCarriesTingInfoAndCountersFromSessionState() {
        stubSeatProfiles();
        GameRoomEntity room = room();
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        session.advance(
                GamePhase.PLAYING,
                1,
                1L,
                """
                {
                  "tingInfosBySeat": {
                    "2": {
                      "seat": 2,
                      "tingMahs": [
                        {"discard": 17, "huTargets": [17, 20]}
                      ]
                    }
                  },
                  "shengPaiCount": 21,
                  "leftBankerCount": 8
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

        GameplaySnapshot ownerSnapshot = service.get(OWNER_ID, "123456");
        assertThat(ownerSnapshot.tingInfo()).isNull();
        assertThat(ownerSnapshot.shengPaiCount()).isEqualTo(21);
        assertThat(ownerSnapshot.leftBankerCount()).isEqualTo(8);

        GameplaySnapshot guestSnapshot = service.get(GUEST_ID, "123456");
        assertThat(guestSnapshot.tingInfo()).isNotNull();
        assertThat(guestSnapshot.tingInfo().path("seat").asInt()).isEqualTo(2);
        assertThat(guestSnapshot.tingInfo().path("tingMahs").get(0).path("discard").asInt())
                .isEqualTo(17);
        assertThat(
                        guestSnapshot
                                .tingInfo()
                                .path("tingMahs")
                                .get(0)
                                .path("huTargets")
                                .get(1)
                                .asInt())
                .isEqualTo(20);
        assertThat(guestSnapshot.shengPaiCount()).isEqualTo(21);
        assertThat(guestSnapshot.leftBankerCount()).isEqualTo(8);
    }

    @Test
    void snapshotLeavesAbsentWaveThreeFieldsAsNull() {
        stubSeatProfiles();
        GameRoomEntity room = room();
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

        GameplaySnapshot snapshot = service.get(OWNER_ID, "123456");
        assertThat(snapshot.tingInfo()).isNull();
        assertThat(snapshot.shengPaiCount()).isNull();
        assertThat(snapshot.leftBankerCount()).isNull();
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

    private static RoomParticipantEntity participant(GameRoomEntity room, UUID userId) {
        return new RoomParticipantEntity(room.getId(), userId);
    }

    private void stubSeatProfiles() {
        when(ownerUser.isActive()).thenReturn(true);
        when(ownerUser.getDisplayName()).thenReturn("房主昵称");
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(ownerUser));
        when(profileService.ensureProfile(OWNER_ID))
                .thenReturn(
                        new PlayerProfileEntity(
                                OWNER_ID, 1084375590L, "avatar_default", 0));
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
