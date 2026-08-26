package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.config.GameplayQaProperties;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QaGameplayBotServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void replacesQaGoldBotsBelowTheSelectedLevelAdmissionBeforeTheNextRound() {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        RoomParticipantRepository participants = mock(RoomParticipantRepository.class);
        QaGameplayBotService service =
                spy(
                        new QaGameplayBotService(
                                new GameplayQaProperties(true),
                                mock(UserRepository.class),
                                identities,
                                mock(PlayerProfileService.class),
                                participants,
                                mock(GameSessionSeatRepository.class)));
        UserEntity firstOldBot = UserEntity.create("清风");
        UserEntity secondOldBot = UserEntity.create("小周");
        UserEntity retainedBot = UserEntity.create("南山");
        UserEntity firstNewBot = UserEntity.create("晚风");
        UserEntity secondNewBot = UserEntity.create("青禾");
        UserIdentityEntity qaIdentity =
                new UserIdentityEntity(firstOldBot, IdentityProvider.QA_BOT, "old-1", null);
        when(identities.findByUser_IdOrderByCreatedAtAsc(firstOldBot.getId()))
                .thenReturn(List.of(qaIdentity));
        when(identities.findByUser_IdOrderByCreatedAtAsc(secondOldBot.getId()))
                .thenReturn(List.of(qaIdentity));
        when(identities.findByUser_IdOrderByCreatedAtAsc(retainedBot.getId()))
                .thenReturn(List.of(qaIdentity));
        doReturn(List.of(firstOldBot, secondOldBot, retainedBot, firstNewBot, secondNewBot))
                .when(service)
                .ensureBotPool(NOW);
        GameRoomEntity room = qaGoldRoom();
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        GameSessionSeatEntity human = seat(session, 1, UserEntity.create("玩家小陈"), 50_000L);
        GameSessionSeatEntity belowAdmission = seat(session, 2, firstOldBot, 29_999L);
        GameSessionSeatEntity broke = seat(session, 3, secondOldBot, 0L);
        GameSessionSeatEntity retained = seat(session, 4, retainedBot, 30_000L);

        List<GameSessionSeatEntity> nextSeats =
                service.replaceIneligibleGoldBots(
                        room,
                        session,
                        List.of(human, belowAdmission, broke, retained),
                        NOW);

        assertThat(nextSeats).extracting(GameSessionSeatEntity::getUserId)
                .containsExactly(
                        human.getUserId(),
                        firstNewBot.getId(),
                        secondNewBot.getId(),
                        retainedBot.getId());
        assertThat(nextSeats).extracting(GameSessionSeatEntity::getScore)
                .containsExactly(50_000L, 30_000L, 30_000L, 30_000L);
        assertThat(List.of(firstNewBot.getDisplayName(), secondNewBot.getDisplayName()))
                .allMatch(name -> !name.toLowerCase().contains("ai"))
                .allMatch(name -> !name.contains("机器人"));
    }

    @Test
    void neverReplacesPlayersInAProductionGoldRoom() {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        QaGameplayBotService service =
                new QaGameplayBotService(
                        new GameplayQaProperties(true),
                        mock(UserRepository.class),
                        identities,
                        mock(PlayerProfileService.class),
                        mock(RoomParticipantRepository.class),
                        mock(GameSessionSeatRepository.class));
        GameRoomEntity room = productionGoldRoom();
        GameSessionEntity session = new GameSessionEntity(room.getId(), 30109L, NOW);
        GameSessionSeatEntity seat = seat(session, 1, UserEntity.create("真实玩家"), 0L);

        List<GameSessionSeatEntity> unchanged =
                service.replaceIneligibleGoldBots(room, session, List.of(seat), NOW);

        assertThat(unchanged).containsExactly(seat);
        verifyNoInteractions(identities);
    }

    private static GameSessionSeatEntity seat(
            GameSessionEntity session, int number, UserEntity user, long score) {
        return new GameSessionSeatEntity(session.getId(), number, user.getId(), score, NOW);
    }

    private static GameRoomEntity qaGoldRoom() {
        return room("QaGoldMatch='1';QaBotMinCoins='30000';");
    }

    private static GameRoomEntity productionGoldRoom() {
        return room("GoldMatch='1';basescore='600';");
    }

    private static GameRoomEntity room(String gameRule) {
        return new GameRoomEntity(
                "123456",
                UUID.randomUUID(),
                900023L,
                30109L,
                gameRule,
                "不平搓/底分600/8圈",
                "{}",
                50,
                4,
                8,
                RoomPayType.ALL,
                0,
                "request-key",
                "request-hash",
                RoomVenue.GOLD);
    }
}
