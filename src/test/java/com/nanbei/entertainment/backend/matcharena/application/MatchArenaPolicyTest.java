package com.nanbei.entertainment.backend.matcharena.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaCostType;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaLevel;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMode;
import org.junit.jupiter.api.Test;

class MatchArenaPolicyTest {
    private final MatchArenaPolicy policy = new MatchArenaPolicy();

    @Test
    void mapsOriginal900023ModesAndPaymentTypes() {
        assertThat(policy.originalPayType(900023, MatchArenaMode.LEADER, MatchArenaCostType.CHAMPION))
                .isZero();
        assertThat(policy.originalPayType(900023, MatchArenaMode.LEADER, MatchArenaCostType.AA))
                .isEqualTo(24);
        assertThat(policy.originalPayType(900023, MatchArenaMode.PREPAID, MatchArenaCostType.AA))
                .isEqualTo(999);
        assertThat(policy.originalPayType(900023, MatchArenaMode.CIRCULATION, MatchArenaCostType.AA))
                .isEqualTo(7);
        assertThat(policy.originalPayType(900023, MatchArenaMode.LOBBY_CARD, MatchArenaCostType.CHAMPION))
                .isEqualTo(23);
        assertThat(policy.originalPayType(900023, MatchArenaMode.LOBBY_CARD, MatchArenaCostType.AA))
                .isEqualTo(22);
    }

    @Test
    void rejectsCombinationsHiddenByOriginal900023Configuration() {
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        request(MatchArenaMode.CIRCULATION, MatchArenaCostType.CHAMPION)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        request(MatchArenaMode.LOBBY_CARD, MatchArenaCostType.AA, 1, 888888)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        request(MatchArenaMode.PREPAID, MatchArenaCostType.AA, 0, 7)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void preservesOriginalNumericRemarkAndDailyLimitValidation() {
        policy.validate(request(MatchArenaMode.LEADER, MatchArenaCostType.CHAMPION));
        policy.validate(request(MatchArenaMode.LEADER, MatchArenaCostType.CHAMPION, 0, 1, "12.3"));
        policy.validate(request(MatchArenaMode.LEADER, MatchArenaCostType.CHAMPION, 0, 999999));

        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        request(
                                                MatchArenaMode.LEADER,
                                                MatchArenaCostType.CHAMPION,
                                                0,
                                                888888,
                                                "比赛")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        request(
                                                MatchArenaMode.LEADER,
                                                MatchArenaCostType.CHAMPION,
                                                0,
                                                888888,
                                                "12345")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void enforcesOriginalVisibilityAutoTransferAndLevelLimits() {
        assertThat(policy.maxOwned(MatchArenaLevel.LEGACY)).isEqualTo(10);
        assertThat(policy.maxOwned(MatchArenaLevel.JUNIOR)).isEqualTo(2);
        assertThat(policy.maxOwned(MatchArenaLevel.INTERMEDIATE)).isEqualTo(2);
        assertThat(policy.maxOwned(MatchArenaLevel.SENIOR)).isEqualTo(5);

        MatchArenaCreateCommand valid = request(MatchArenaMode.LEADER, MatchArenaCostType.AA);
        policy.validate(valid);
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        copy(valid, false, false, 50, 0)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        copy(valid, true, false, 100, 0)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        copy(valid, true, false, 50, 100)))
                .isInstanceOf(ApiException.class);
        MatchArenaCreateCommand lobby =
                request(MatchArenaMode.LOBBY_CARD, MatchArenaCostType.AA);
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        copy(lobby, true, true, 50, 100)))
                .isInstanceOf(ApiException.class);
    }

    private static MatchArenaCreateCommand request(
            MatchArenaMode mode, MatchArenaCostType costType) {
        return request(mode, costType, 0, 888888);
    }

    private static MatchArenaCreateCommand request(
            MatchArenaMode mode,
            MatchArenaCostType costType,
            long initialRoomCards,
            long dailyLimit) {
        return request(mode, costType, initialRoomCards, dailyLimit, "888");
    }

    private static MatchArenaCreateCommand request(
            MatchArenaMode mode,
            MatchArenaCostType costType,
            long initialRoomCards,
            long dailyLimit,
            String remark) {
        return new MatchArenaCreateCommand(
                900023,
                remark,
                MatchArenaLevel.JUNIOR,
                mode,
                costType,
                initialRoomCards,
                dailyLimit,
                true,
                false,
                50,
                0,
                null);
    }

    private static MatchArenaCreateCommand copy(
            MatchArenaCreateCommand source,
            boolean visible,
            boolean autoTransfer,
            long threshold,
            long amount) {
        return new MatchArenaCreateCommand(
                source.lobbyId(),
                source.remark(),
                source.level(),
                source.mode(),
                source.costType(),
                source.initialRoomCards(),
                source.dailyRoomCardLimit(),
                visible,
                autoTransfer,
                threshold,
                amount,
                source.lowCardReminderThreshold());
    }
}
