package com.nanbei.entertainment.backend.gameplay.taizhoumahjong;

import com.nanbei.entertainment.backend.gameplay.domain.GameDefinition;
import java.util.List;

/** Original client contract for Taizhou Mahjong ConfID 30109. */
public final class TaizhouMahjongDefinition implements GameDefinition {
    public static final long GAME_ID = 30109L;

    private static final List<Integer> PLAYER_COUNTS = List.of(2, 4);

    @Override
    public long gameId() {
        return GAME_ID;
    }

    @Override
    public List<Integer> playerCounts() {
        return PLAYER_COUNTS;
    }
}
