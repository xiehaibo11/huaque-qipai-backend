package com.nanbei.entertainment.backend.gameplay.application;

/**
 * 原版客户端 {@code GameDefine.POWER} 位掩码常量（Android {@code MahjongPower} 已按
 * {@code GameDefine.luac:98-113} 逐值移植），后端 QA 引擎用同一组位值下发动作权限。
 * 常量本身是原版协议事实；哪些位在什么时机下发是南北自建 QA 规则。
 */
final class QaPowerMask {
    static final int NONE = 0x000;
    static final int CANCEL = 0x001;
    static final int PLAY = 0x002;
    static final int CHOW = 0x004;
    static final int PUNG = 0x008;
    static final int HU = 0x010;
    static final int MKONG = 0x020;
    static final int CKONG = 0x040;
    static final int TKONG = 0x080;
    static final int REPLACE = 0x800;

    private QaPowerMask() {}

    static boolean has(int mask, int bit) {
        return (mask & bit) != 0;
    }
}
