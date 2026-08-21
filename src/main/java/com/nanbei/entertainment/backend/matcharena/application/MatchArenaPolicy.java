package com.nanbei.entertainment.backend.matcharena.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaCostType;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaLevel;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class MatchArenaPolicy {
    static final long ORIGINAL_LOBBY_ID = 900023L;
    static final long ORIGINAL_DAILY_SENTINEL = 888888L;
    static final long ORIGINAL_AUTO_TRANSFER_THRESHOLD = 50L;

    public void validate(MatchArenaCreateCommand command) {
        if (command.lobbyId() != ORIGINAL_LOBBY_ID) {
            throw new ApiException(
                    ErrorCode.MATCH_ARENA_CREATION_UNAVAILABLE,
                    "当前地区暂未开放创建比赛场");
        }
        validateRemark(command.remark());
        require(command.level() != null, "请选择比赛场等级");
        require(command.mode() != null, "请选择比赛场模式");
        require(command.costType() != null, "请先为您的比赛场选择消耗模式");
        require(
                command.initialRoomCards() >= 0
                        && command.initialRoomCards() <= Long.MAX_VALUE / 100L,
                "划卡数量不正确");
        if (command.mode() == MatchArenaMode.LOBBY_CARD) {
            require(command.initialRoomCards() == 0, "当前模式不可划入房卡");
            require(!command.autoTransferEnabled(), "当前模式不可开启自动补卡");
        }
        if (command.mode() == MatchArenaMode.LEADER) {
            require(command.dailyRoomCardLimit() > 0, "每日最大消耗输入不正确");
        } else {
            require(
                    command.dailyRoomCardLimit() == ORIGINAL_DAILY_SENTINEL,
                    "仅领队模式可设置每日最大消耗");
        }
        require(command.visibleToStrangers(), "当前地区比赛场必须对陌生人可见");
        require(
                command.autoTransferThreshold() == ORIGINAL_AUTO_TRANSFER_THRESHOLD,
                "自动补卡阈值不正确");
        if (command.autoTransferEnabled()) {
            require(command.autoTransferAmount() > 0, "请填写正确补卡数值");
        } else {
            require(command.autoTransferAmount() == 0, "未开启自动补卡时转入数量必须为0");
        }
        if (command.lowCardReminderThreshold() != null) {
            require(command.lowCardReminderThreshold() > 0, "请填写正确提醒数值");
        }
        originalPayType(command.lobbyId(), command.mode(), command.costType());
    }

    public int maxOwned(MatchArenaLevel level) {
        return switch (level) {
            case LEGACY -> 10;
            case JUNIOR, INTERMEDIATE -> 2;
            case SENIOR -> 5;
        };
    }

    public int originalPayType(
            long lobbyId, MatchArenaMode mode, MatchArenaCostType costType) {
        if (lobbyId != ORIGINAL_LOBBY_ID || mode == null || costType == null) {
            throw notAllowed();
        }
        return switch (mode) {
            case LEADER -> costType == MatchArenaCostType.CHAMPION ? 0 : 24;
            case PREPAID -> costType == MatchArenaCostType.CHAMPION ? 0 : 999;
            case CIRCULATION -> {
                if (costType != MatchArenaCostType.AA) {
                    throw notAllowed();
                }
                yield 7;
            }
            case LOBBY_CARD -> costType == MatchArenaCostType.CHAMPION ? 23 : 22;
        };
    }

    private static void validateRemark(String remark) {
        String value = remark == null ? "" : remark.trim();
        require(
                value.getBytes(StandardCharsets.UTF_8).length <= 4,
                "比赛场备注不能超过4个字符");
        require(value.isEmpty() || value.matches("(?:\\d+(?:\\.\\d*)?|\\.\\d+)"),
                "比赛场备注只允许数字和小数点");
    }

    private static ApiException notAllowed() {
        return new ApiException(
                ErrorCode.MATCH_ARENA_MODE_NOT_ALLOWED,
                "当前比赛场模式不支持所选消耗方式");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
    }
}
