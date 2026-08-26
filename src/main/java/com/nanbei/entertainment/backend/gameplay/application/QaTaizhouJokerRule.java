package com.nanbei.entertainment.backend.gameplay.application;

import java.util.List;

/** 本局翻牌确定的财神与白板替代牌映射。 */
record QaTaizhouJokerRule(int jokerTile, int insteadTile) {
    static final int WHITE = 0x53;

    QaTaizhouJokerRule {
        if (jokerTile != 0
                && jokerTile != QaTaizhouTiles.JOKER
                && !QaTaizhouTiles.isPlayable(jokerTile)) {
            throw new IllegalArgumentException(
                    "joker tile must be empty, playable, or synthetic joker");
        }
        if (jokerTile == 0 && insteadTile != 0) {
            throw new IllegalArgumentException("unrevealed joker cannot have an instead tile");
        }
        if (insteadTile != 0
                && (!QaTaizhouTiles.isPlayable(insteadTile) || jokerTile == WHITE)) {
            throw new IllegalArgumentException("instead tile is invalid for this joker");
        }
        if (insteadTile != 0 && insteadTile != WHITE) {
            throw new IllegalArgumentException("Taizhou joker instead tile must be white dragon");
        }
    }

    static QaTaizhouJokerRule fromOpenTile(int openTile) {
        if (!QaTaizhouTiles.isPlayable(openTile)) {
            throw new IllegalArgumentException("opened wall tile must be playable");
        }
        return new QaTaizhouJokerRule(openTile, openTile == WHITE ? 0 : WHITE);
    }

    static QaTaizhouJokerRule synthetic() {
        return new QaTaizhouJokerRule(QaTaizhouTiles.JOKER, 0);
    }

    static QaTaizhouJokerRule unrevealed() {
        return new QaTaizhouJokerRule(0, 0);
    }

    boolean isJoker(int tile) {
        return jokerTile != 0 && tile == jokerTile;
    }

    boolean hasJoker(List<Integer> tiles) {
        return jokerTile != 0 && tiles.contains(jokerTile);
    }

    boolean hasInstead(List<Integer> tiles) {
        return insteadTile != 0 && tiles.contains(insteadTile);
    }

    int normalizedOrdinaryTile(int tile) {
        return tile == insteadTile && insteadTile != 0 ? jokerTile : tile;
    }

    List<Integer> jokerTiles() {
        return jokerTile == 0 ? List.of() : List.of(jokerTile);
    }

    List<Integer> insteadTiles() {
        return insteadTile == 0 ? List.of() : List.of(insteadTile);
    }
}
