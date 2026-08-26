package com.nanbei.entertainment.backend.gameplay.application;

import java.util.Map;

enum TaizhouRoundMode {
    QA(
            true,
            "QA",
            TaizhouWallShuffle.ALGORITHM,
            "QA_TAIZHOU_V2",
            "南北自建 QA 规则，非原版服务端算法：牌墙（136 无花牌固定 seed）、轮转、"
                    + "裁决优先级（胡>杠>碰>吃）、胡判定（基本型+财神）与台州大众玩法"
                    + "胡数番数结算都是南北娱乐为端到端测试自建的实现。"),
    SERVER_AUTHORITY(
            false,
            "SERVER_AUTHORITY",
            TaizhouWallShuffle.ALGORITHM,
            "SERVER_TAIZHOU_V1",
            "南北娱乐自研服务端权威规则：参考已逆向确认的浙江游戏大厅客户端协议、"
                    + "Lua、CSB、资源、实机和协议证据实现；不是恢复出的原版服务端源码。");

    private final boolean qaMode;
    private final String engineMode;
    private final String wallAlgorithm;
    private final String winTrigger;
    private final String disclosure;

    TaizhouRoundMode(
            boolean qaMode,
            String engineMode,
            String wallAlgorithm,
            String winTrigger,
            String disclosure) {
        this.qaMode = qaMode;
        this.engineMode = engineMode;
        this.wallAlgorithm = wallAlgorithm;
        this.winTrigger = winTrigger;
        this.disclosure = disclosure;
    }

    boolean qaMode() {
        return qaMode;
    }

    String engineMode() {
        return engineMode;
    }

    String wallAlgorithm() {
        return wallAlgorithm;
    }

    String winTrigger() {
        return winTrigger;
    }

    String disclosure() {
        return disclosure;
    }

    void putMarkers(Map<String, Object> payload) {
        payload.put("qaMode", qaMode);
        payload.put("engineMode", engineMode);
        if (qaMode) {
            payload.put("qaDisclosure", disclosure);
        } else {
            payload.put("serverAuthority", true);
            payload.put("rulesDisclosure", disclosure);
        }
    }

    static TaizhouRoundMode fromSessionState(tools.jackson.databind.JsonNode state) {
        String engineMode = state.path("engineMode").asText("");
        if (SERVER_AUTHORITY.engineMode.equals(engineMode)) {
            return SERVER_AUTHORITY;
        }
        if (QA.engineMode.equals(engineMode)
                || state.path("qaMode").asBoolean(false)
                || !state.path("qaDisclosure").isMissingNode()) {
            return QA;
        }
        return null;
    }
}
