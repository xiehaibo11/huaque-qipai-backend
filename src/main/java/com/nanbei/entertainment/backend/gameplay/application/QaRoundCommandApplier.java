package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * QA 真人命令校验与应用（南北自建规则，非原版服务端算法）。
 * actionToken 一次性：与未消费的 pending offer 匹配才受理，消费后整桌 offer 由裁决关闭。
 */
final class QaRoundCommandApplier {
    private static final Set<String> MULTIPLE_CHOICES = Set.of("NONE", "ADD", "SUPER");

    void apply(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            int actorSeat,
            GameplayCommandType type,
            JsonNode payload,
            long revision,
            List<GameEvent> events) {
        switch (type) {
            case DISCARD -> discard(turnDriver, table, context, actorSeat, payload, revision, events);
            case CHOW -> claim(table, context, actorSeat, payload, revision, events,
                    QaClaim.Kind.CHOW, turnDriver);
            case PUNG -> claim(table, context, actorSeat, payload, revision, events,
                    QaClaim.Kind.PUNG, turnDriver);
            case KONG -> kong(turnDriver, table, context, actorSeat, payload, revision, events);
            case HU -> hu(turnDriver, table, context, actorSeat, payload, revision, events);
            case PASS -> pass(turnDriver, table, context, actorSeat, payload, revision, events);
            case MULTIPLE_CHOICE -> multipleChoice(turnDriver, table, context, actorSeat, payload, revision, events);
            default ->
                    throw new ApiException(
                            ErrorCode.VALIDATION_FAILED, "不支持的 QA 牌局命令 " + type);
        }
    }

    private void discard(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            int actorSeat,
            JsonNode payload,
            long revision,
            List<GameEvent> events) {
        QaRoundTable.PendingOffer offer = requireOffer(table, actorSeat, token(payload));
        if (!offer.playOffer || !QaPowerMask.has(offer.powerMask, QaPowerMask.PLAY)) {
            throw notAllowed("当前没有出牌权限");
        }
        int tile = requiredInt(payload, "tileValue");
        if (!table.hands().get(actorSeat).contains(tile) || !QaTaizhouTiles.isPlayable(tile)) {
            throw notAllowed("手里没有这张牌");
        }
        table.offers().remove(actorSeat);
        turnDriver.discard(table, context, revision, events, actorSeat, tile);
    }

    private void claim(
            QaRoundTable table,
            QaRoundContext context,
            int actorSeat,
            JsonNode payload,
            long revision,
            List<GameEvent> events,
            QaClaim.Kind kind,
            QaRoundTurnDriver turnDriver) {
        QaRoundTable.PendingOffer offer = requireOffer(table, actorSeat, token(payload));
        if (offer.playOffer) {
            throw notAllowed("当前不在吃碰杠窗口");
        }
        int maskBit =
                switch (kind) {
                    case CHOW -> QaPowerMask.CHOW;
                    case PUNG -> QaPowerMask.PUNG;
                    case KONG -> QaPowerMask.MKONG;
                    case HU -> QaPowerMask.HU;
                };
        if (!QaPowerMask.has(offer.powerMask, maskBit)) {
            throw notAllowed("当前没有该动作权限");
        }
        int tile = requiredInt(payload, "tileValue");
        if (offer.contextTile == null || offer.contextTile != tile) {
            throw notAllowed("目标牌与当前窗口不一致");
        }
        if (kind == QaClaim.Kind.CHOW) {
            int candidateIndex = requiredInt(payload, "candidateIndex");
            if (candidateIndex < 0 || candidateIndex >= offer.chowCandidates.size()) {
                throw notAllowed("吃候选下标越界");
            }
            offer.candidateIndex = candidateIndex;
        }
        offer.claimKind = kind;
        adjudicateIfAnswered(turnDriver, table, context, revision, events);
    }

    private void kong(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            int actorSeat,
            JsonNode payload,
            long revision,
            List<GameEvent> events) {
        String kongType = requiredText(payload, "kongType");
        int tile = requiredInt(payload, "tileValue");
        QaRoundTable.PendingOffer offer = requireOffer(table, actorSeat, token(payload));
        if ("EXPOSED".equals(kongType)) {
            claim(table, context, actorSeat, payload, revision, events, QaClaim.Kind.KONG, turnDriver);
            return;
        }
        if (!offer.playOffer) {
            throw notAllowed("暗杠/补杠只能在自己的出牌回合");
        }
        QaMeldCandidates.KongOption option = null;
        for (QaMeldCandidates.KongOption candidate : offer.kongOptions) {
            if (candidate.kongType().equals(kongType) && candidate.tileValue() == tile) {
                option = candidate;
                break;
            }
        }
        if (option == null) {
            throw notAllowed("当前没有该杠选项");
        }
        List<Integer> hand = table.hands().get(actorSeat);
        QaRoundTable.Meld meld;
        if ("CONCEALED".equals(kongType)) {
            for (int index = 0; index < 4; index++) {
                hand.remove(Integer.valueOf(tile));
            }
            meld = new QaRoundTable.Meld(
                    "CONCEALED_KONG", List.of(tile, tile, tile, tile), actorSeat);
        } else {
            QaRoundTable.Meld pong = null;
            for (QaRoundTable.Meld existing : table.melds().get(actorSeat)) {
                if (existing.combType().equals("PONG") && existing.tiles().get(0) == tile) {
                    pong = existing;
                    break;
                }
            }
            if (pong == null || !hand.remove(Integer.valueOf(tile))) {
                throw notAllowed("补杠缺少碰或手牌");
            }
            table.melds().get(actorSeat).remove(pong);
            meld = new QaRoundTable.Meld("FILL_KONG", List.of(tile, tile, tile, tile), actorSeat);
        }
        table.melds().get(actorSeat).add(meld);
        table.offers().remove(actorSeat);
        turnDriver.applyOwnKong(table, context, revision, events, actorSeat, meld);
    }

    private void hu(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            int actorSeat,
            JsonNode payload,
            long revision,
            List<GameEvent> events) {
        QaRoundTable.PendingOffer offer = requireOffer(table, actorSeat, token(payload));
        if (!QaPowerMask.has(offer.powerMask, QaPowerMask.HU)) {
            throw notAllowed("当前没有胡牌权限");
        }
        if (offer.playOffer) {
            if (!QaWinDetector.canWin(table.hands().get(actorSeat))) {
                throw notAllowed("自建胡判定未通过，无法胡牌");
            }
            table.offers().remove(actorSeat);
            turnDriver.declareWin(table, context, revision, events, actorSeat, "ZIMO", null);
            return;
        }
        java.util.ArrayList<Integer> withTile =
                new java.util.ArrayList<>(table.hands().get(actorSeat));
        withTile.add(offer.contextTile);
        if (!QaWinDetector.canWin(withTile)) {
            throw notAllowed("自建胡判定未通过，无法胡牌");
        }
        offer.claimKind = QaClaim.Kind.HU;
        adjudicateIfAnswered(turnDriver, table, context, revision, events);
    }

    private void pass(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            int actorSeat,
            JsonNode payload,
            long revision,
            List<GameEvent> events) {
        QaRoundTable.PendingOffer offer = requireOffer(table, actorSeat, token(payload));
        if (offer.playOffer || !QaPowerMask.has(offer.powerMask, QaPowerMask.CANCEL)) {
            throw notAllowed("当前没有可放弃的动作窗口");
        }
        offer.passed = true;
        turnDriver.expireOffer(table, revision, events, actorSeat, offer.offerId);
        adjudicateIfAnswered(turnDriver, table, context, revision, events);
    }

    private void multipleChoice(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            int actorSeat,
            JsonNode payload,
            long revision,
            List<GameEvent> events) {
        if (table.stage != QaRoundTable.Stage.AWAIT_MULTIPLE) {
            throw notAllowed("当前不在加倍选择阶段");
        }
        if (table.choices().containsKey(actorSeat)) {
            throw notAllowed("已经选择过加倍");
        }
        String choice = requiredText(payload, "choice");
        if (!MULTIPLE_CHOICES.contains(choice)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "不支持的加配选择 " + choice);
        }
        table.choices().put(actorSeat, choice);
        turnDriver.multipleChoiceChanged(table, context, revision, events);
    }

    private void adjudicateIfAnswered(
            QaRoundTurnDriver turnDriver,
            QaRoundTable table,
            QaRoundContext context,
            long revision,
            List<GameEvent> events) {
        if (!table.hasUnansweredHumanOffer()) {
            turnDriver.adjudicate(table, context, revision, events);
        }
    }

    private QaRoundTable.PendingOffer requireOffer(QaRoundTable table, int actorSeat, String token) {
        QaRoundTable.PendingOffer offer = table.offers().get(actorSeat);
        if (offer == null || offer.answered() || !offer.actionToken.equals(token)) {
            throw notAllowed("动作凭证无效或已被消费");
        }
        return offer;
    }

    private static String token(JsonNode payload) {
        return requiredText(payload, "actionToken");
    }

    private static int requiredInt(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || !value.isNumber()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "命令缺少数值字段 " + field);
        }
        return value.asInt();
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "命令缺少文本字段 " + field);
        }
        return value.asText();
    }

    private static ApiException notAllowed(String message) {
        return new ApiException(ErrorCode.GAME_ACTION_NOT_ALLOWED, message);
    }
}
