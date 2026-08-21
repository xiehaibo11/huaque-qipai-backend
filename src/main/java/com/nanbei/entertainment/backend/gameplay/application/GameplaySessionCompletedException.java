package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;

/**
 * QA 牌局局数用尽（南北自建多局流转）：会话已在同事务内置为 COMPLETED，
 * 本异常在 {@code GameplayCommandService} 上声明为不回滚，保证完结状态落库后
 * 客户端仍收到 GAME_ACTION_NOT_ALLOWED。大结算暂未实现。
 */
final class GameplaySessionCompletedException extends ApiException {
    GameplaySessionCompletedException(String message) {
        super(ErrorCode.GAME_ACTION_NOT_ALLOWED, message);
    }
}
