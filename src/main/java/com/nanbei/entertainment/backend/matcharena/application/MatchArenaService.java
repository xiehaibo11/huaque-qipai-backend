package com.nanbei.entertainment.backend.matcharena.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaCardLedgerEntity;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaEntity;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMemberEntity;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMemberRole;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMemberStatus;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaStatus;
import com.nanbei.entertainment.backend.matcharena.infrastructure.MatchArenaCardLedgerRepository;
import com.nanbei.entertainment.backend.matcharena.infrastructure.MatchArenaMemberRepository;
import com.nanbei.entertainment.backend.matcharena.infrastructure.MatchArenaRepository;
import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import com.nanbei.entertainment.backend.region.infrastructure.RegionLobbyRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchArenaService {
    private final MatchArenaRepository arenaRepository;
    private final MatchArenaMemberRepository memberRepository;
    private final MatchArenaCardLedgerRepository ledgerRepository;
    private final PlayerWalletRepository walletRepository;
    private final PlayerProfileRepository profileRepository;
    private final PlayerProfileService profileService;
    private final UserRepository userRepository;
    private final RegionLobbyRepository lobbyRepository;
    private final UserRegionSelectionRepository selectionRepository;
    private final MatchArenaPolicy policy;
    private final CryptoService cryptoService;

    public MatchArenaService(
            MatchArenaRepository arenaRepository,
            MatchArenaMemberRepository memberRepository,
            MatchArenaCardLedgerRepository ledgerRepository,
            PlayerWalletRepository walletRepository,
            PlayerProfileRepository profileRepository,
            PlayerProfileService profileService,
            UserRepository userRepository,
            RegionLobbyRepository lobbyRepository,
            UserRegionSelectionRepository selectionRepository,
            MatchArenaPolicy policy,
            CryptoService cryptoService) {
        this.arenaRepository = arenaRepository;
        this.memberRepository = memberRepository;
        this.ledgerRepository = ledgerRepository;
        this.walletRepository = walletRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.userRepository = userRepository;
        this.lobbyRepository = lobbyRepository;
        this.selectionRepository = selectionRepository;
        this.policy = policy;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public MatchArenaListResponse list(UUID userId) {
        return new MatchArenaListResponse(
                memberRepository.findVisibleByUserId(userId).stream()
                        .map(member -> response(requireArena(member.getArenaId()), member.getRole(), false))
                        .toList());
    }

    @Transactional
    public MatchArenaResponse create(
            UUID userId, MatchArenaCreateCommand command, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        String requestHash = cryptoService.sha256(canonical(command));
        arenaRepository.acquireOwnerCreateLock("match-arena-owner:" + userId);
        var existing =
                arenaRepository.findByOwnerUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new ApiException(
                        ErrorCode.MATCH_ARENA_IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key 已用于不同的创建请求");
            }
            return response(existing.get(), MatchArenaMemberRole.OWNER, true);
        }

        requireActiveUser(userId);
        profileService.ensureProfile(userId);
        RegionLobbyEntity lobby = currentLobby(userId);
        if (lobby.getLobbyId() != command.lobbyId()) {
            throw new ApiException(
                    ErrorCode.MATCH_ARENA_REGION_MISMATCH,
                    "只能在当前选择的地区创建比赛场");
        }
        policy.validate(command);
        if (arenaRepository.countByOwnerUserIdAndLevelAndStatusNot(
                        userId, command.level(), MatchArenaStatus.DISSOLVED)
                >= policy.maxOwned(command.level())) {
            throw new ApiException(ErrorCode.MATCH_ARENA_LIMIT_REACHED, "已达到比赛场创建上限");
        }

        PlayerWalletEntity wallet = lockedWallet(userId);
        validateAutoTransferBalance(wallet, command);
        debitInitialCards(wallet, command.initialRoomCards());
        int arenaNumber = allocateArenaNumber();
        MatchArenaEntity arena =
                arenaRepository.save(
                        new MatchArenaEntity(
                                arenaNumber,
                                userId,
                                command.lobbyId(),
                                command.remark(),
                                command.level(),
                                command.mode(),
                                command.costType(),
                                policy.originalPayType(
                                        command.lobbyId(), command.mode(), command.costType()),
                                command.dailyRoomCardLimit(),
                                command.initialRoomCards(),
                                command.visibleToStrangers(),
                                command.autoTransferEnabled(),
                                command.autoTransferThreshold(),
                                command.autoTransferAmount(),
                                command.lowCardReminderThreshold(),
                                idempotencyKey,
                                requestHash));
        memberRepository.save(
                new MatchArenaMemberEntity(
                        arena.getId(), userId, MatchArenaMemberRole.OWNER));
        if (command.initialRoomCards() > 0) {
            ledgerRepository.save(
                    new MatchArenaCardLedgerEntity(
                            arena.getId(), userId, command.initialRoomCards()));
            walletRepository.save(wallet);
        }
        return response(arena, MatchArenaMemberRole.OWNER, false);
    }

    private MatchArenaResponse response(
            MatchArenaEntity arena, MatchArenaMemberRole role, boolean duplicate) {
        UserEntity owner = requireUser(arena.getOwnerUserId());
        var profile =
                profileRepository.findById(arena.getOwnerUserId())
                        .orElseThrow(
                                () -> new ApiException(
                                        ErrorCode.AUTH_INVALID_CREDENTIAL,
                                        "用户资料不存在"));
        RegionLobbyEntity lobby =
                lobbyRepository.findById(arena.getLobbyId())
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.REGION_NOT_FOUND, "比赛场地区不存在"));
        return new MatchArenaResponse(
                arena.getId().toString(),
                "%06d".formatted(arena.getArenaNumber()),
                arena.getLobbyId(),
                lobby.getAreaName(),
                arena.getRemark(),
                arena.getLevel(),
                arena.getMode(),
                arena.getCostType(),
                arena.getOriginalPayType(),
                role,
                profile.getPublicPlayerId(),
                owner.getDisplayName(),
                profile.getAvatarKey(),
                arena.getRoomCards(),
                arena.getDailyRoomCardLimit(),
                arena.isVisibleToStrangers(),
                arena.isAutoTransferEnabled(),
                arena.getAutoTransferThreshold(),
                arena.getAutoTransferAmount(),
                arena.getLowCardReminderThreshold(),
                arena.getStatus(),
                Math.toIntExact(
                        memberRepository.countByArenaIdAndStatus(
                                arena.getId(), MatchArenaMemberStatus.ACTIVE)),
                Math.toIntExact(memberRepository.countOnlineByArenaId(arena.getId())),
                arena.getCreatedAt(),
                arena.getVersion(),
                duplicate);
    }

    private RegionLobbyEntity currentLobby(UUID userId) {
        return selectionRepository.findById(userId)
                .flatMap(selection -> lobbyRepository.findByLobbyIdAndEnabledTrue(selection.getLobbyId()))
                .orElseGet(
                        () -> lobbyRepository.findFirstByDefaultLobbyTrueAndEnabledTrueOrderBySortOrderAsc()
                                .orElseThrow(
                                        () -> new ApiException(ErrorCode.REGION_NOT_FOUND, "没有可用的默认地区")));
    }

    private UserEntity requireActiveUser(UUID userId) {
        UserEntity user = requireUser(userId);
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.USER_DISABLED, "账号已停用");
        }
        return user;
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIAL, "用户不存在"));
    }

    private PlayerWalletEntity lockedWallet(UUID userId) {
        return walletRepository.findLockedByUserId(userId)
                .orElseGet(() -> walletRepository.save(new PlayerWalletEntity(userId, 0, 0, 0, 0)));
    }

    private static void debitInitialCards(PlayerWalletEntity wallet, long amount) {
        if (amount == 0) {
            return;
        }
        try {
            wallet.debitRoomCards(amount);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ApiException(
                    ErrorCode.MATCH_ARENA_INSUFFICIENT_ROOM_CARDS,
                    "房卡库存不足");
        }
    }

    private static void validateAutoTransferBalance(
            PlayerWalletEntity wallet, MatchArenaCreateCommand command) {
        if (command.autoTransferEnabled()
                && command.autoTransferAmount() > wallet.getRoomCards()) {
            throw new ApiException(
                    ErrorCode.MATCH_ARENA_INSUFFICIENT_ROOM_CARDS,
                    "个人账户余额不足");
        }
    }

    private int allocateArenaNumber() {
        long value;
        try {
            value = arenaRepository.nextArenaNumber();
        } catch (RuntimeException exception) {
            throw new ApiException(
                    ErrorCode.MATCH_ARENA_NUMBER_EXHAUSTED,
                    "比赛场号码已用尽");
        }
        if (value < 100000 || value > 999999) {
            throw new ApiException(ErrorCode.MATCH_ARENA_NUMBER_EXHAUSTED, "比赛场号码已用尽");
        }
        return Math.toIntExact(value);
    }

    private MatchArenaEntity requireArena(UUID arenaId) {
        return arenaRepository.findById(arenaId)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.MATCH_ARENA_NOT_FOUND, "比赛场不存在"));
    }

    private static String canonical(MatchArenaCreateCommand command) {
        return command.lobbyId() + "|" + normalized(command.remark()) + "|" + command.level()
                + "|" + command.mode() + "|" + command.costType() + "|" + command.initialRoomCards()
                + "|" + command.dailyRoomCardLimit() + "|" + command.visibleToStrangers()
                + "|" + command.autoTransferEnabled() + "|" + command.autoTransferThreshold()
                + "|" + command.autoTransferAmount() + "|" + command.lowCardReminderThreshold();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不合法");
        }
    }
}
