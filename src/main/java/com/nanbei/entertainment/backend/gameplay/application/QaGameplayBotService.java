package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.config.GameplayQaProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantId;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QaGameplayBotService {
    private static final String BOT_SUBJECT_PREFIX = "taizhou-mahjong-bot-";
    private static final List<String> BOT_DISPLAY_NAMES =
            List.of(
                    "A-伟娜",
                    "Mr.Chan",
                    "阿白",
                    "WhimSeeker",
                    "星星的爷爷",
                    "白薇",
                    "大叔徐浩",
                    "乐乐",
                    "清风",
                    "小周",
                    "南山",
                    "北巷",
                    "晚风",
                    "小陈",
                    "阿杰",
                    "青禾",
                    "小林",
                    "海棠",
                    "子安",
                    "小沈",
                    "晨光",
                    "阿宁",
                    "时雨",
                    "云川",
                    "小韩",
                    "嘉木");

    private final GameplayQaProperties properties;
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final PlayerProfileService profileService;
    private final RoomParticipantRepository participantRepository;
    private final GameSessionSeatRepository seatRepository;

    QaGameplayBotService(
            GameplayQaProperties properties,
            UserRepository userRepository,
            UserIdentityRepository identityRepository,
            PlayerProfileService profileService,
            RoomParticipantRepository participantRepository,
            GameSessionSeatRepository seatRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.profileService = profileService;
        this.participantRepository = participantRepository;
        this.seatRepository = seatRepository;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(
            fixedDelayString = "${nanbei.gameplay.qa.bot-heartbeat-delay:PT2M}",
            initialDelayString = "${nanbei.gameplay.qa.bot-heartbeat-initial-delay:PT30S}")
    @Transactional
    public void refreshBotPoolPresence() {
        if (enabled()) {
            ensureBotPool(Instant.now());
        }
    }

    public List<GameSessionSeatEntity> ensureTenBotsAndFillSeats(
            GameRoomEntity room,
            GameSessionEntity session,
            List<GameSessionSeatEntity> currentSeats,
            Instant occurredAt) {
        return ensureTenBotsAndFillSeats(room, session, currentSeats, occurredAt, null);
    }

    public List<GameSessionSeatEntity> ensureTenBotsAndFillSeats(
            GameRoomEntity room,
            GameSessionEntity session,
            List<GameSessionSeatEntity> currentSeats,
            Instant occurredAt,
            UUID preferredBotUserId) {
        if (!enabled()) {
            throw new ApiException(
                    ErrorCode.GAME_ACTION_NOT_ALLOWED,
                    "QA 自动牌局只允许在测试/调试配置中启用");
        }
        List<UserEntity> botUsers = preferredFirst(ensureBotPool(occurredAt), preferredBotUserId);
        List<GameSessionSeatEntity> seats = new ArrayList<>(currentSeats);
        if (seats.size() > room.getPlayerCount()) {
            throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "牌局座位数超过房间人数");
        }
        Set<UUID> seated = new HashSet<>();
        for (GameSessionSeatEntity seat : seats) {
            seated.add(seat.getUserId());
        }
        int nextSeat =
                seats.stream().mapToInt(seat -> seat.getId().getSeatNumber()).max().orElse(0) + 1;
        List<GameSessionSeatEntity> additions = new ArrayList<>();
        for (UserEntity bot : botUsers) {
            if (seats.size() >= room.getPlayerCount()) {
                break;
            }
            if (!seated.add(bot.getId())) {
                continue;
            }
            RoomParticipantId participantId = new RoomParticipantId(room.getId(), bot.getId());
            if (!participantRepository.existsById(participantId)) {
                participantRepository.save(new RoomParticipantEntity(room.getId(), bot.getId()));
            }
            GameSessionSeatEntity seat =
                    new GameSessionSeatEntity(session.getId(), nextSeat++, bot.getId(), occurredAt);
            seat.setConnected(true, occurredAt);
            seat.setReady(true, occurredAt);
            additions.add(seat);
            seats.add(seat);
        }
        if (seats.size() != room.getPlayerCount()) {
            throw new ApiException(ErrorCode.ROOM_NOT_FULL, "QA 自动牌局需要先补齐座位");
        }
        for (GameSessionSeatEntity seat : seats) {
            if (!seat.isReady()) {
                seat.setReady(true, occurredAt);
            }
            if (!seat.isConnected()) {
                seat.setConnected(true, occurredAt);
            }
        }
        if (!additions.isEmpty()) {
            seatRepository.saveAll(additions);
        }
        return seats.stream()
                .sorted(java.util.Comparator.comparingInt(seat -> seat.getId().getSeatNumber()))
                .toList();
    }

    private static List<UserEntity> preferredFirst(
            List<UserEntity> botUsers, UUID preferredBotUserId) {
        if (preferredBotUserId == null) {
            return botUsers;
        }
        List<UserEntity> ordered = new ArrayList<>(botUsers.size());
        botUsers.stream()
                .filter(bot -> bot.getId().equals(preferredBotUserId))
                .findFirst()
                .ifPresent(ordered::add);
        botUsers.stream()
                .filter(bot -> !bot.getId().equals(preferredBotUserId))
                .forEach(ordered::add);
        return ordered;
    }

    public List<QaMahjongAutoRoundEngine.SeatInput> seatInputs(
            GameRoomEntity room, List<GameSessionSeatEntity> seats) {
        return seats.stream()
                .map(seat -> seatInput(room, seat))
                .toList();
    }

    private QaMahjongAutoRoundEngine.SeatInput seatInput(
            GameRoomEntity room, GameSessionSeatEntity seat) {
        UserEntity user =
                userRepository
                        .findById(seat.getUserId())
                        .filter(UserEntity::isActive)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.ROOM_ILLEGAL_STATE,
                                                "QA 牌局玩家资料不完整"));
        PlayerProfileEntity profile = profileService.ensureProfile(user.getId());
        return new QaMahjongAutoRoundEngine.SeatInput(
                seat.getId().getSeatNumber(),
                user.getId(),
                user.getDisplayName(),
                profile.getPublicPlayerId(),
                seat.getScore(),
                isQaBot(user.getId()));
    }

    private boolean isQaBot(UUID userId) {
        return identityRepository.findByUser_IdOrderByCreatedAtAsc(userId).stream()
                .anyMatch(identity -> identity.getProvider() == IdentityProvider.QA_BOT);
    }

    List<UserEntity> ensureBotPool(Instant occurredAt) {
        List<UserEntity> bots = new ArrayList<>(QaMahjongAutoRoundEngine.BOT_POOL_SIZE);
        for (int index = 1; index <= QaMahjongAutoRoundEngine.BOT_POOL_SIZE; index++) {
            int botIndex = index;
            String subject = BOT_SUBJECT_PREFIX + String.format("%03d", botIndex);
            UserEntity user =
                    identityRepository
                            .findByProviderAndProviderSubject(IdentityProvider.QA_BOT, subject)
                            .map(UserIdentityEntity::getUser)
                            .orElseGet(() -> createBot(subject, botIndex));
            user.rename(botDisplayName(botIndex));
            user.setLastActiveAt(occurredAt);
            profileService.ensureProfile(user.getId());
            bots.add(user);
        }
        userRepository.saveAll(bots);
        return bots;
    }

    private UserEntity createBot(String subject, int index) {
        UserEntity user = userRepository.save(UserEntity.create(botDisplayName(index)));
        identityRepository.save(new UserIdentityEntity(user, IdentityProvider.QA_BOT, subject, null));
        return user;
    }

    private static String botDisplayName(int index) {
        String name = BOT_DISPLAY_NAMES.get((index - 1) % BOT_DISPLAY_NAMES.size());
        int cycle = (index - 1) / BOT_DISPLAY_NAMES.size();
        return cycle == 0 ? name : name + (cycle + 1);
    }
}
