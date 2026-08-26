package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantId;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class QaGoldBotLifecycle {
    private static final Pattern QA_BOT_MIN_COINS =
            Pattern.compile("(?:^|;)QaBotMinCoins=['\"]?(\\d+)['\"]?(?:;|$)");

    private QaGoldBotLifecycle() {}

    static List<GameSessionSeatEntity> replace(
            boolean enabled,
            GameRoomEntity room,
            List<GameSessionSeatEntity> currentSeats,
            Instant occurredAt,
            Supplier<List<UserEntity>> botPool,
            Predicate<UUID> isQaBot,
            RoomParticipantRepository participantRepository) {
        long minCoins = minCoins(room.getGameRule());
        if (!enabled || minCoins < 0L) {
            return List.copyOf(currentSeats);
        }
        List<GameSessionSeatEntity> seats = new ArrayList<>(currentSeats);
        Set<UUID> unavailable = new HashSet<>();
        seats.forEach(seat -> unavailable.add(seat.getUserId()));
        List<UserEntity> replacements = botPool.get();
        for (GameSessionSeatEntity seat : seats) {
            if (!isQaBot.test(seat.getUserId())
                    || (seat.getScore() > 0L && seat.getScore() >= minCoins)) {
                continue;
            }
            UserEntity replacement =
                    replacements.stream()
                            .filter(bot -> unavailable.add(bot.getId()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    ErrorCode.ROOM_NOT_FULL,
                                                    "测试牌友池暂无可用补位"));
            participantRepository.deleteById(
                    new RoomParticipantId(room.getId(), seat.getUserId()));
            participantRepository.save(
                    new RoomParticipantEntity(room.getId(), replacement.getId()));
            seat.replaceOccupant(replacement.getId(), minCoins, occurredAt);
        }
        return seats.stream()
                .sorted(java.util.Comparator.comparingInt(seat -> seat.getId().getSeatNumber()))
                .toList();
    }

    private static long minCoins(String gameRule) {
        if (gameRule == null || !gameRule.contains("QaGoldMatch='1'")) {
            return -1L;
        }
        Matcher matcher = QA_BOT_MIN_COINS.matcher(gameRule);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1L;
    }
}
