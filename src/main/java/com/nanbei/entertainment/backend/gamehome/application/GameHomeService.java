package com.nanbei.entertainment.backend.gamehome.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.friend.application.FriendPresenceService;
import com.nanbei.entertainment.backend.gamehome.domain.GameHomeEntryEntity;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.GameHomeEntryRepository;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import com.nanbei.entertainment.backend.region.infrastructure.RegionLobbyRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameHomeService {
    private final UserRepository userRepository;
    private final PlayerProfileService profileService;
    private final PlayerWalletRepository walletRepository;
    private final GameHomeEntryRepository entryRepository;
    private final RegionLobbyRepository lobbyRepository;
    private final UserRegionSelectionRepository selectionRepository;
    private final FriendPresenceService friendPresenceService;

    public GameHomeService(
            UserRepository userRepository,
            PlayerProfileService profileService,
            PlayerWalletRepository walletRepository,
            GameHomeEntryRepository entryRepository,
            RegionLobbyRepository lobbyRepository,
            UserRegionSelectionRepository selectionRepository,
            FriendPresenceService friendPresenceService) {
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.walletRepository = walletRepository;
        this.entryRepository = entryRepository;
        this.lobbyRepository = lobbyRepository;
        this.selectionRepository = selectionRepository;
        this.friendPresenceService = friendPresenceService;
    }

    @Transactional
    public GameHomeSnapshot load(UUID userId) {
        UserEntity user =
                userRepository
                        .findById(userId)
                        .filter(UserEntity::isActive)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_INVALID_CREDENTIAL,
                                                "用户不存在或已被禁用"));
        friendPresenceService.touch(user.getId());
        PlayerProfileEntity profile = profileService.ensureProfile(userId);
        PlayerWalletEntity wallet =
                walletRepository
                        .findById(userId)
                        .orElseGet(
                                () ->
                                        walletRepository.save(
                                                new PlayerWalletEntity(
                                                        userId,
                                                        0L,
                                                        0L,
                                                        0L,
                                                        0L)));
        RegionLobbyEntity lobby = resolveLobby(userId);
        List<GameHomeSnapshot.Entry> entries =
                entryRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                        .filter(
                                entry ->
                                        entry.getLobbyId() == null
                                                || entry.getLobbyId()
                                                        == lobby.getLobbyId())
                        .map(GameHomeService::toEntry)
                        .toList();
        return new GameHomeSnapshot(
                new GameHomeSnapshot.Player(
                        userId,
                        profile.getPublicPlayerId(),
                        user.getDisplayName(),
                        profile.getAvatarKey(),
                        profile.getMembershipLevel()),
                new GameHomeSnapshot.Wallet(
                        wallet.getRoomCards(),
                        wallet.getBoundRoomCards(),
                        wallet.getCoins(),
                        wallet.getDiamonds()),
                new GameHomeSnapshot.Region(
                        lobby.getLobbyId(), lobby.getAreaName()),
                entries);
    }

    private RegionLobbyEntity resolveLobby(UUID userId) {
        return selectionRepository
                .findById(userId)
                .flatMap(
                        selection ->
                                lobbyRepository.findByLobbyIdAndEnabledTrue(
                                        selection.getLobbyId()))
                .orElseGet(
                        () ->
                                lobbyRepository
                                        .findFirstByDefaultLobbyTrueAndEnabledTrueOrderBySortOrderAsc()
                                        .orElseThrow(
                                                () ->
                                                        new ApiException(
                                                                ErrorCode.REGION_NOT_FOUND,
                                                                "没有可用的默认地区")));
    }

    private static GameHomeSnapshot.Entry toEntry(
            GameHomeEntryEntity entry) {
        return new GameHomeSnapshot.Entry(
                entry.getCode(),
                entry.getDisplayName(),
                entry.getEntryType(),
                entry.getRoute(),
                entry.getIconKey(),
                entry.getSortOrder(),
                entry.isEnabled(),
                entry.getBubbleText(),
                entry.getBubbleType(),
                entry.getBubbleIntervalSeconds());
    }
}
