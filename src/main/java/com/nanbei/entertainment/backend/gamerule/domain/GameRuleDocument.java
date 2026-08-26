package com.nanbei.entertainment.backend.gamerule.domain;

import java.util.List;

public record GameRuleDocument(long gameId, String title, List<Block> blocks) {
    public GameRuleDocument {
        blocks = List.copyOf(blocks);
    }

    public record Block(String type, String text) {}
}
