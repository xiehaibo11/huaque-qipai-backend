package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameCommandEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameCommandRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 客户端转发族命令，对齐原版 {@code msgClientForward}（XY_ID=1043，字段 seat/id/strData）：
 * 表情、GPS、语音等客户端互动经服务器原样转给同房间所有人，不落地桌态。
 *
 * <p>实现上占用下一个修订号（R+1）但把 {@code session.state} 原样回写：事件流游标
 * (revision, eventOrder) 只推一格，桌态快照零变化，轮询玩家拿到的
 * {@code GET /events?afterRevision=R} 也能看见 (R+1, 1)。副作用是并发出牌的
 * {@code expectedRevision} 基线被顶掉一个号，等同原版转发族打断本地 nActionID
 * 序列的行为，客户端会拿到 GAME_COMMAND_STALE 后重试。
 *
 * <p>命令必须运行在 {@link GameplayCommandService#submit} 的事务与 session 行锁内，
 * 因此由该服务在分支里同步调用，不单独开事务。
 */
final class GameplayClientForwardHandler {
    private final GameCommandRepository commandRepository;
    private final GameEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    GameplayClientForwardHandler(
            GameCommandRepository commandRepository,
            GameEventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.commandRepository = commandRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    GameplayCommandResponse submit(
            UUID userId,
            String safeKey,
            String requestHash,
            GameplayCommandRequest request,
            GameSessionEntity session,
            GameSessionSeatEntity actorSeat,
            Instant now) {
        if (request.payload() == null || !request.payload().isObject()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "转发内容缺失");
        }
        JsonNode payload = request.payload();
        if (!payload.hasNonNull("cfId") || !payload.path("cfId").canConvertToInt()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "cfId 必须是数字");
        }
        int cfId = payload.path("cfId").asInt();
        // 原版 msgClientForward(1043) 的 CF_ID 值域 1..10：FastVoice/GPS_MSG/Mobile_Signal/
        // Speed_Test/WireBreak_Signal/Expression/FaceAni/PlayerHeadEffect/PropAni/
        // PlayerHeadTrust（BasicMahjong/Protocols/GameProtocol.luac:1651-1663；扩展层
        // msgBaseClientForwardEx=22 的 CF_ID 到 15+OPERATE_PASS=160，本命令不覆盖）。
        if (cfId < 1 || cfId > 10) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "cfId 超出原版 CF_ID 值域");
        }
        String data = payload.path("data").asText("");
        if (data.length() > 512) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "转发内容过长");
        }
        long nextRevision = session.getRevision() + 1;
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("seatNumber", actorSeat.getId().getSeatNumber());
        eventPayload.put("cfId", cfId);
        eventPayload.put("data", data);
        GameEvent event = GameEvent.publicEvent(nextRevision, "CLIENT_FORWARD", eventPayload);
        session.advance(
                session.getPhase(),
                session.getRoundNumber(),
                nextRevision,
                session.getState(),
                now);
        eventRepository.save(eventEntity(session.getId(), event, 1, now));
        GameplayCommandResponse response =
                new GameplayCommandResponse(
                        nextRevision,
                        event.type(),
                        actorSeat.getId().getSeatNumber(),
                        actorSeat.isReady(),
                        false);
        GameCommandEntity command =
                new GameCommandEntity(
                        session.getId(),
                        userId,
                        safeKey,
                        requestHash,
                        request.type().name(),
                        request.expectedRevision(),
                        now);
        command.accept(nextRevision, json(response));
        commandRepository.save(command);
        return response;
    }

    private GameEventEntity eventEntity(
            UUID sessionId, GameEvent event, int eventOrder, Instant occurredAt) {
        return GameEventEntity.publicEvent(
                sessionId,
                event.revision(),
                eventOrder,
                event.type(),
                json(event.payload()),
                occurredAt);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize gameplay state", exception);
        }
    }
}
