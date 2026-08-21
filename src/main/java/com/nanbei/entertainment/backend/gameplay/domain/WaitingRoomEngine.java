package com.nanbei.entertainment.backend.gameplay.domain;

import java.util.List;
import java.util.Map;

public final class WaitingRoomEngine implements GameEngine<WaitingRoomState> {
    @Override
    public CommandResult<WaitingRoomState> handle(
            WaitingRoomState state, GameCommand command) {
        if (!(command instanceof ReadyCommand)
                && !(command instanceof AutoReadyCommand)) {
            throw new GameActionNotAllowedException("等待阶段不支持该操作");
        }
        if (state.phase() != GamePhase.WAITING) {
            throw new GameActionNotAllowedException("当前不在等待准备阶段");
        }
        command.requireRevision(state.revision());
        int seatNumber =
                state.seatOf(command.actorUserId())
                        .orElseThrow(
                                () -> new GameActionNotAllowedException("用户不在牌局座位中"));
        if (command instanceof AutoReadyCommand) {
            return autoReady(state);
        }
        ReadyCommand readyCommand = (ReadyCommand) command;
        if (state.isReady(seatNumber) == readyCommand.ready()) {
            throw new GameActionNotAllowedException("座位准备状态没有变化");
        }
        WaitingRoomState next = state.withReady(seatNumber, readyCommand.ready());
        GameEvent event =
                GameEvent.publicEvent(
                        next.revision(),
                        "SEAT_READY_CHANGED",
                        Map.of("seatNumber", seatNumber, "ready", readyCommand.ready()));
        return CommandResult.accepted(state, next, List.of(event));
    }

    private static CommandResult<WaitingRoomState> autoReady(WaitingRoomState state) {
        List<Integer> changedSeats =
                state.occupants().keySet().stream()
                        .filter(seat -> !state.isReady(seat))
                        .sorted()
                        .toList();
        if (changedSeats.isEmpty()) {
            throw new GameActionNotAllowedException("座位准备状态没有变化");
        }
        WaitingRoomState next = state.withAllOccupiedReady();
        List<GameEvent> events =
                changedSeats.stream()
                        .map(
                                seat ->
                                        GameEvent.publicEvent(
                                                next.revision(),
                                                "SEAT_READY_CHANGED",
                                                Map.of("seatNumber", seat, "ready", true)))
                        .toList();
        return CommandResult.accepted(state, next, events);
    }
}
