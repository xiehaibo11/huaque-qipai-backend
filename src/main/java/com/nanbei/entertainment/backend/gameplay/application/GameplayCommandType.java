package com.nanbei.entertainment.backend.gameplay.application;

public enum GameplayCommandType {
    READY,
    UNREADY,
    START_ROUND,
    QA_AUTO_ROUND,
    NEXT_ROUND,
    EARLY_START,
    DISCARD,
    CHOW,
    PUNG,
    KONG,
    HU,
    PASS,
    MULTIPLE_CHOICE,
    /** 客户端转发族（原版 msgClientForward XY_ID=1043 / msgBaseClientForwardEx XY_ID=22）。 */
    CLIENT_FORWARD
}
