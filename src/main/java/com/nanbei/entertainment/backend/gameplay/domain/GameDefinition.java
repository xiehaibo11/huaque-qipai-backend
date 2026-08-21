package com.nanbei.entertainment.backend.gameplay.domain;

import java.util.List;

/** Stable client contract shared by gameplay engines. */
public interface GameDefinition {
    long gameId();

    List<Integer> playerCounts();
}
