package com.nanbei.entertainment.backend.gamehome.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.friend.application.FriendPresenceService;
import com.nanbei.entertainment.backend.gamehome.domain.GameHomeEntryEntity;
import com.nanbei.entertainment.backend.gamehome.domain.LobbyAnnouncementEntity;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.GameHomeEntryRepository;
import com.nanbei.entertainment.backend.gamehome.infrastructure.LobbyAnnouncementRepository;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import com.nanbei.entertainment.backend.region.domain.UserRegionSelectionEntity;
import com.nanbei.entertainment.backend.region.infrastructure.RegionLobbyRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameHomeServiceTest {
    @Mock UserRepository userRepository;
    @Mock PlayerProfileService profileService;
    @Mock PlayerWalletRepository walletRepository;
    @Mock GameHomeEntryRepository entryRepository;
    @Mock LobbyAnnouncementRepository announcementRepository;
    @Mock RegionLobbyRepository lobbyRepository;
    @Mock UserRegionSelectionRepository selectionRepository;
    @Mock FriendPresenceService friendPresenceService;

    GameHomeService service;

    @BeforeEach
    void setUp() {
        service =
                new GameHomeService(
                        userRepository,
                        profileService,
                        walletRepository,
                        entryRepository,
                        announcementRepository,
                        lobbyRepository,
                        selectionRepository,
                        friendPresenceService);
    }

    @Test
    void createsPersistentProfileAndWalletAndReturnsTheSelectedLobby() {
        UserEntity user = UserEntity.create("手机用户8000");
        UserRegionSelectionEntity selection =
                new UserRegionSelectionEntity(user.getId(), 900025L);
        RegionLobbyEntity hangzhouBaby =
                new RegionLobbyEntity(
                        900025L,
                        "hangzhou",
                        "杭州(宝宝)",
                        16,
                        true,
                        false);
        GameHomeEntryEntity join =
                new GameHomeEntryEntity(
                        "JOIN_ROOM",
                        "加入房间",
                        "PRIMARY",
                        "room/join",
                        "home_icon_join_room",
                        20,
                        true,
                        null);
        GameHomeEntryEntity create =
                new GameHomeEntryEntity(
                        "CREATE_ROOM",
                        "创建房间",
                        "PRIMARY",
                        "room/create",
                        "home_icon_create_room",
                        10,
                        true,
                        null);

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));
        when(profileService.ensureProfile(user.getId()))
                .thenReturn(
                        new PlayerProfileEntity(
                                user.getId(),
                                1084375590L,
                                "avatar_default",
                                0));
        when(walletRepository.findById(user.getId()))
                .thenReturn(Optional.empty());
        when(walletRepository.save(any(PlayerWalletEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(selectionRepository.findById(user.getId()))
                .thenReturn(Optional.of(selection));
        when(lobbyRepository.findByLobbyIdAndEnabledTrue(900025L))
                .thenReturn(Optional.of(hangzhouBaby));
        when(entryRepository.findByEnabledTrueOrderBySortOrderAsc())
                .thenReturn(List.of(create, join));
        when(announcementRepository.findByEnabledTrueOrderBySortOrderAscIdAsc())
                .thenReturn(
                        List.of(
                                new LobbyAnnouncementEntity(
                                        "游戏公告:适当游戏益脑，沉迷游戏伤身",
                                        null,
                                        10,
                                        true,
                                        null,
                                        null)));

        GameHomeSnapshot result = service.load(user.getId());

        assertThat(result.player().displayName()).isEqualTo("手机用户8000");
        assertThat(result.player().publicPlayerId()).isEqualTo(1084375590L);
        assertThat(result.wallet().roomCards()).isZero();
        assertThat(result.wallet().boundRoomCards()).isZero();
        assertThat(result.wallet().coins()).isZero();
        assertThat(result.wallet().diamonds()).isZero();
        assertThat(result.region().lobbyId()).isEqualTo(900025L);
        assertThat(result.region().areaName()).isEqualTo("杭州(宝宝)");
        assertThat(result.entries())
                .extracting(GameHomeSnapshot.Entry::code)
                .containsExactly("CREATE_ROOM", "JOIN_ROOM");
        assertThat(result.announcements())
                .extracting(GameHomeSnapshot.Announcement::content)
                .containsExactly("游戏公告:适当游戏益脑，沉迷游戏伤身");
        verify(profileService).ensureProfile(user.getId());
        verify(walletRepository).save(any(PlayerWalletEntity.class));
        verify(friendPresenceService).touch(user.getId());
    }

    @Test
    void exposesPerEntryBubbleConfigurationAndDefaultsToNoBubble() {
        UserEntity user = UserEntity.create("手机用户8001");
        GameHomeEntryEntity silent =
                new GameHomeEntryEntity(
                        "CREATE_ROOM",
                        "创建房间",
                        "PRIMARY",
                        "room/create",
                        "home_icon_create_room",
                        10,
                        true,
                        null);
        // 原版 hall_tip_type_2 气泡按入口配置下发：文案、播放类型和间隔秒数。
        GameHomeEntryEntity announced =
                new GameHomeEntryEntity(
                        "MATCH",
                        "比赛场",
                        "PRIMARY",
                        "match",
                        "home_game_mahjong",
                        30,
                        true,
                        null,
                        "排位赛S32赛季8月1号正式开启！",
                        3,
                        30);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileService.ensureProfile(user.getId()))
                .thenReturn(
                        new PlayerProfileEntity(user.getId(), 1084375591L, "avatar_default", 0));
        when(walletRepository.findById(user.getId())).thenReturn(Optional.empty());
        when(walletRepository.save(any(PlayerWalletEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(selectionRepository.findById(user.getId()))
                .thenReturn(Optional.of(new UserRegionSelectionEntity(user.getId(), 900023L)));
        when(lobbyRepository.findByLobbyIdAndEnabledTrue(900023L))
                .thenReturn(
                        Optional.of(
                                new RegionLobbyEntity(
                                        900023L, "taizhou", "台州", 16, true, false)));
        when(entryRepository.findByEnabledTrueOrderBySortOrderAsc())
                .thenReturn(List.of(silent, announced));
        when(announcementRepository.findByEnabledTrueOrderBySortOrderAscIdAsc())
                .thenReturn(List.of());

        GameHomeSnapshot result = service.load(user.getId());

        GameHomeSnapshot.Entry first = result.entries().get(0);
        assertThat(first.bubbleText()).isNull();
        assertThat(first.bubbleType()).isNull();
        assertThat(first.bubbleIntervalSeconds()).isNull();

        GameHomeSnapshot.Entry second = result.entries().get(1);
        assertThat(second.bubbleText()).isEqualTo("排位赛S32赛季8月1号正式开启！");
        assertThat(second.bubbleType()).isEqualTo(3);
        assertThat(second.bubbleIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void returnsOnlyAnnouncementsVisibleForTheSelectedLobbyAndCurrentTime() {
        UserEntity user = UserEntity.create("手机用户8002");
        Instant now = Instant.parse("2026-08-24T12:00:00Z");
        RegionLobbyEntity taizhou =
                new RegionLobbyEntity(900023L, "taizhou", "台州", 16, true, false);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileService.ensureProfile(user.getId()))
                .thenReturn(
                        new PlayerProfileEntity(user.getId(), 1084375592L, "avatar_default", 0));
        when(walletRepository.findById(user.getId()))
                .thenReturn(Optional.of(new PlayerWalletEntity(user.getId(), 0, 0, 0, 0)));
        when(selectionRepository.findById(user.getId()))
                .thenReturn(Optional.of(new UserRegionSelectionEntity(user.getId(), 900023L)));
        when(lobbyRepository.findByLobbyIdAndEnabledTrue(900023L))
                .thenReturn(Optional.of(taizhou));
        when(entryRepository.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(announcementRepository.findByEnabledTrueOrderBySortOrderAscIdAsc())
                .thenReturn(
                        List.of(
                                new LobbyAnnouncementEntity(
                                        "全服公告", null, 10, true, null, null),
                                new LobbyAnnouncementEntity(
                                        "台州公告",
                                        900023L,
                                        20,
                                        true,
                                        now.minusSeconds(60),
                                        now.plusSeconds(60)),
                                new LobbyAnnouncementEntity(
                                        "其他地区", 900025L, 30, true, null, null),
                                new LobbyAnnouncementEntity(
                                        "尚未生效",
                                        null,
                                        40,
                                        true,
                                        now.plusSeconds(1),
                                        null),
                                new LobbyAnnouncementEntity(
                                        "已经结束",
                                        null,
                                        50,
                                        true,
                                        null,
                                        now)));

        GameHomeSnapshot result = service.load(user.getId(), now);

        assertThat(result.announcements())
                .extracting(GameHomeSnapshot.Announcement::content)
                .containsExactly("全服公告", "台州公告");
    }
}
