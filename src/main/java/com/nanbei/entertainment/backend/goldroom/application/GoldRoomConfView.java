package com.nanbei.entertainment.backend.goldroom.application;

import java.util.List;

/**
 * The whole selectable configuration of one gold-room game.
 *
 * <p>Mirrors what the original client assembles as {@code roomConf}: the game header plus the
 * ordered {@code roomLevelInfos}. {@code roomFlags} is the original {@code roomInfo.roomFlag}
 * array and fixes the left-to-right card order on the choose-room page.
 *
 * <p>{@code showsPlayerCount} is false until real gold matchmaking exists. The original hides
 * {@code _panelPlayerCount} by default in {@code ChooseRoom.csb} and only fills it from a live
 * count, so leaving it hidden matches the original rather than inventing a number.
 */
public record GoldRoomConfView(
        GoldGameView game, List<Integer> roomFlags, List<GoldLevelView> levels,
        boolean showsPlayerCount) {}
