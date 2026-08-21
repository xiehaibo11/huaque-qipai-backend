package com.nanbei.entertainment.backend.room.application;

import java.util.List;

public record RoomCreateCommand(
        long lobbyId,
        long gameId,
        int categoryIndex,
        List<String> selectedNodeNames) {
    public RoomCreateCommand {
        selectedNodeNames = List.copyOf(selectedNodeNames);
    }

    String canonicalValue() {
        String selections =
                selectedNodeNames.stream().sorted().reduce("", (left, right) -> left + "\u001f" + right);
        return lobbyId + "|" + gameId + "|" + categoryIndex + "|" + selections;
    }
}
