package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantId;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;

import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoldRoomMatchServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Mock private GameRoomRepository roomRepository;
    @Mock private RoomParticipantRepository participantRepository;
    @Mock private GameSessionRepository sessionRepository;
    @Mock private GameSessionSeatRepository seatRepository;
    @Mock private GameEventRepository eventRepository;
    @Mock private QaGameplayBotService botService;

    private GoldRoomMatchService service;

    @BeforeEach
    void setUp() {
        when(roomRepository.findLiveRoomsForParticipantAndQaMatch(
                        any(), anyLong(), anyInt(), anyString(), any()))
                .thenReturn(List.of());
        when(roomRepository.findMatchableGoldRooms(anyLong(), anyInt(), anyString(), any()))
                .thenReturn(List.of());
        when(roomRepository.nextRoomNumber()).thenReturn("123456");
        when(roomRepository.existsByRoomNumber("123456")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(GameRoomEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(GameSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.findByRoomId(any())).thenReturn(Optional.empty());
        when(participantRepository.countByIdRoomId(any())).thenReturn(1L);
        service =
                new GoldRoomMatchService(
                        roomRepository,
                        participantRepository,
                        sessionRepository,
                        seatRepository,
                        eventRepository,
                        botService,
                        new ObjectMapper(),
                        Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void twoPlayerMatchBuildsTwoPlayerRuleWithBaoRulesDisabled() {
        GameRoomEntity room = createMatch(2);

        assertThat(room.getGameRule())
                .isEqualTo(
                        "winLostType='1';playerCount_2;maxQuanShu='2';liaoDaZiBaoPai='0';"
                                + "buSiBao='0';FengDing='0';PayType='0';autoReady='1';forceGPS='1';"
                                + "IsSysTrust='0';GoldMatch='1';basescore='200';");
    }

    @Test
    void fourPlayerMatchKeepsFourPlayerBaoRulesEnabled() {
        GameRoomEntity room = createMatch(4);

        assertThat(room.getGameRule())
                .isEqualTo(
                        "winLostType='1';playerCount_4;maxQuanShu='2';liaoDaZiBaoPai='1';"
                                + "buSiBao='1';FengDing='0';PayType='0';autoReady='1';forceGPS='1';"
                                + "IsSysTrust='0';GoldMatch='1';basescore='200';");
    }

    @Test
    void matchRejectsPlayerStillQueuedInAnotherLevel() {
        GameRoomEntity otherLevel = goldRoom("gold-match:900023:30109:2", "GoldMatch='1';");
        when(roomRepository.findActiveRoomsForParticipant(
                        USER_ID, RoomVenue.GOLD, RoomStatus.DISSOLVED))
                .thenReturn(List.of(otherLevel));

        assertThatThrownBy(
                        () -> service.match(
                                USER_ID, 900023L, 30109L, 1, 4, 200L, 10_000L, "match-key"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo(ErrorCode.GOLD_QUEUING);
                            assertThat(exception.getMessage())
                                    .isEqualTo("加入失败，玩家仍在队列中");
                        });
    }

    @Test
    void matchRejectsPlayerGamingAtAnotherTable() {
        GameRoomEntity otherTable = goldRoom("gold-match:900023:30109:2", "GoldMatch='1';");
        GameSessionEntity session = new GameSessionEntity(otherTable.getId(), 30109L, NOW);
        session.advance(GamePhase.DEALING, 1, 1L, "{}", NOW);
        when(roomRepository.findActiveRoomsForParticipant(
                        USER_ID, RoomVenue.GOLD, RoomStatus.DISSOLVED))
                .thenReturn(List.of(otherTable));
        when(sessionRepository.findByRoomId(otherTable.getId()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(
                        () -> service.match(
                                USER_ID, 900023L, 30109L, 1, 4, 200L, 10_000L, "match-key"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo(ErrorCode.GOLD_OTHERS_GAMING);
                            assertThat(exception.getMessage())
                                    .isEqualTo("您正在参与其他场次游戏");
                        });
    }

    @Test
    void matchIgnoresQaTestRoomsWhenGatingTheQueue() {
        GameRoomEntity qaRoom =
                goldRoom("qa-gold-match:1", "GoldMatch='1';QaGoldMatch='1';");
        when(roomRepository.findActiveRoomsForParticipant(
                        USER_ID, RoomVenue.GOLD, RoomStatus.DISSOLVED))
                .thenReturn(List.of(qaRoom));

        GameRoomEntity room = createMatch(4);

        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
    }

    @Test
    void leaveRemovesThePlaceholderAndDissolvesTheEmptyRoom() {
        GameRoomEntity room = goldRoom("gold-match:900023:30109:1", "GoldMatch='1';");
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        GameSessionSeatEntity seat =
                new GameSessionSeatEntity(session.getId(), 1, USER_ID, 10_000L, NOW);
        when(roomRepository.findLiveRoomsForParticipantAndQaMatch(
                        USER_ID, 30109L, 50, "gold-match:900023:30109:1", RoomStatus.DISSOLVED))
                .thenReturn(List.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), USER_ID))
                .thenReturn(Optional.of(seat));
        when(participantRepository.countByIdRoomId(room.getId())).thenReturn(0L);

        service.leave(USER_ID, 900023L, 30109L, 1);

        verify(participantRepository).deleteById(new RoomParticipantId(room.getId(), USER_ID));
        verify(seatRepository).delete(seat);
        verify(roomRepository).saveAndFlush(room);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.DISSOLVED);
    }

    @Test
    void leaveIsRejectedOnceTheRoundHasStarted() {
        GameRoomEntity room = goldRoom("gold-match:900023:30109:1", "GoldMatch='1';");
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        session.advance(GamePhase.DEALING, 1, 1L, "{}", NOW);
        when(roomRepository.findLiveRoomsForParticipantAndQaMatch(
                        USER_ID, 30109L, 50, "gold-match:900023:30109:1", RoomStatus.DISSOLVED))
                .thenReturn(List.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.leave(USER_ID, 900023L, 30109L, 1))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo(ErrorCode.GOLD_GAMING);
                            assertThat(exception.getMessage())
                                    .isEqualTo("牌局已开始，请回到牌局继续游戏");
                        });
        verify(participantRepository, never()).deleteById(any());
    }

    @Test
    void leaveWithoutAnyLiveRoomSucceedsQuietly() {
        service.leave(USER_ID, 900023L, 30109L, 1);

        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    void leaveTransfersOwnershipToTheLowestSeatLeftBehind() {
        GameRoomEntity room = goldRoom("gold-match:900023:30109:1", "GoldMatch='1';");
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        when(roomRepository.findLiveRoomsForParticipantAndQaMatch(
                        USER_ID, 30109L, 50, "gold-match:900023:30109:1", RoomStatus.DISSOLVED))
                .thenReturn(List.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), USER_ID))
                .thenReturn(
                        Optional.of(
                                new GameSessionSeatEntity(
                                        session.getId(), 1, USER_ID, 10_000L, NOW)));
        // 房主座位删除后只留下 2 号位，继任房主取留下的最小座位号（原版队列的房主维护）。
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(
                        List.of(
                                new GameSessionSeatEntity(
                                        session.getId(), 2, OTHER_ID, 10_000L, NOW)));
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(new RoomParticipantEntity(room.getId(), OTHER_ID)));

        service.leave(USER_ID, 900023L, 30109L, 1);

        assertThat(room.getOwnerUserId()).isEqualTo(OTHER_ID);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
        verify(roomRepository).saveAndFlush(room);
    }

    @Test
    void leaveTransfersOwnershipToSmallestUserIdWithoutSeats() {
        GameRoomEntity room = goldRoom("gold-match:900023:30109:1", "GoldMatch='1';");
        when(roomRepository.findLiveRoomsForParticipantAndQaMatch(
                        USER_ID, 30109L, 50, "gold-match:900023:30109:1", RoomStatus.DISSOLVED))
                .thenReturn(List.of(room));
        when(participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()))
                .thenReturn(List.of(new RoomParticipantEntity(room.getId(), OTHER_ID)));

        service.leave(USER_ID, 900023L, 30109L, 1);

        assertThat(room.getOwnerUserId()).isEqualTo(OTHER_ID);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
    }

    @Test
    void sweepDissolvesTimedOutMatchingRooms() {
        GameRoomEntity room = goldRoom("gold-match:900023:30109:1", "GoldMatch='1';");
        when(roomRepository.findTimedOutGoldMatchingRooms(any(), anyString(), any()))
                .thenReturn(List.of(room));
        when(roomRepository.findByRoomNumber("654321")).thenReturn(Optional.of(room));
        when(participantRepository.countByIdRoomId(room.getId())).thenReturn(1L);

        int dissolved = service.sweepTimedOutRooms(NOW);

        assertThat(dissolved).isEqualTo(1);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.DISSOLVED);
        verify(roomRepository).saveAndFlush(room);
    }

    @Test
    void sweepKeepsFullRoomsThatJustStarted() {
        GameRoomEntity room = goldRoom("gold-match:900023:30109:1", "GoldMatch='1';");
        when(roomRepository.findTimedOutGoldMatchingRooms(any(), anyString(), any()))
                .thenReturn(List.of(room));
        when(roomRepository.findByRoomNumber("654321")).thenReturn(Optional.of(room));
        when(participantRepository.countByIdRoomId(room.getId())).thenReturn(4L);

        int dissolved = service.sweepTimedOutRooms(NOW);

        assertThat(dissolved).isZero();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
        verify(roomRepository, never()).saveAndFlush(any());
    }

    private GameRoomEntity goldRoom(String creationRequestHash, String gameRule) {
        return new GameRoomEntity(
                "654321",
                USER_ID,
                900023L,
                30109L,
                gameRule,
                "display",
                "roomrule={}",
                50,
                4,
                1,
                RoomPayType.ALL,
                0,
                "gold-leave-test",
                creationRequestHash,
                RoomVenue.GOLD);
    }

    private GameRoomEntity createMatch(int chairCount) {
        service.match(USER_ID, 900023L, 30109L, 1, chairCount, 200L, 10_000L, "match-key");
        ArgumentCaptor<GameRoomEntity> roomCaptor = ArgumentCaptor.forClass(GameRoomEntity.class);
        verify(roomRepository).saveAndFlush(roomCaptor.capture());
        return roomCaptor.getValue();
    }
}
