package com.nanbei.entertainment.backend.goldroom.application;

import java.util.List;

/**
 * The whole selectable configuration of one gold-room game.
 *
 * <p>Mirrors what the original client assembles as {@code roomConf}: the game header plus the
 * ordered {@code roomLevelInfos}. {@code roomFlags} is the original {@code roomInfo.roomFlag}
 * array and fixes the left-to-right card order on the choose-room page.
 *
 * <p>{@code showsPlayerCount} follows the mode-50 live count response. The original hides
 * {@code _panelPlayerCount} before that response is available.
 */
public record GoldRoomConfView(
        GoldGameView game, List<Integer> roomFlags, List<GoldLevelView> levels,
        boolean showsPlayerCount) {}
