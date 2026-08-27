package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameCommandRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.TaizhouMahjongRuleDisplay;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 原版服务端超时托管代打的等价物（自建）：真人出牌权过了宽限期由服务端代打刚摸的牌，
 * 弃牌/抢杠窗口里未作答的真人动作按过处理，随后把事件循环推进到下一个真人动作点。
 */
@Service
public class QaRoundTimeoutService {
    private static final Logger log = LoggerFactory.getLogger(QaRoundTimeoutService.class);

    private final GameSessionRepository sessionRepository;
    private final GameRoomRepository roomRepository;
    private final GameSessionSeatRepository seatRepository;
    private final QaGameplayBotService qaBotService;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();
    private final GameplayRoundOutcomePersister persister;

    @Autowired
    private GoldRoomWalletSettlementService goldWalletSettlementService;

    QaRoundTimeoutService(
            GameSessionRepository sessionRepository,
            GameRoomRepository roomRepository,
            GameSessionSeatRepository seatRepository,
            GameEventRepository eventRepository,
            GameCommandRepository commandRepository,
            QaGameplayBotService qaBotService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.qaBotService = qaBotService;
        this.objectMapper = objectMapper;
        this.persister =
                new GameplayRoundOutcomePersister(eventRepository, commandRepository, objectMapper);
    }

    /** 到期裁决单会话；锁内复判后才推进，无到期时保持只读。 */
    @Transactional
    public void expireDueOffer(UUID sessionId) {
        GameSessionEntity session = sessionRepository.findLockedById(sessionId).orElse(null);
        if (session == null || session.getPhase() != GamePhase.PLAYING) {
            return;
        }
        JsonNode state;
        try {
            state = objectMapper.readTree(session.getState());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read gameplay session state", exception);
        }
        TaizhouRoundMode mode = TaizhouRoundMode.fromSessionState(state);
        if (mode == null || state.path("qaRound").isMissingNode() || (mode.qaMode() && !enabled())) {
            return;
        }
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(objectMapper, mode);
        QaRoundTable table = engine.readTable(state);
        Instant now = clock.instant();
        if (!engine.hasTimedOutOffers(table, now)) {
            return;
        }
        GameRoomEntity room =
                roomRepository
                        .findById(session.getRoomId())
                        .orElseThrow(() -> new IllegalStateException("session room missing"));
        var seats = seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId());
        QaRoundContext context =
                new QaRoundContext(
                        room.getRoomNumber(),
                        gameRuleDisplay(room),
                        qaBotService.seatInputs(room, seats),
                        now,
                        room.getRoomMode() == 50);
        long nextRevision = session.getRevision() + 1L;
        List<GameEvent> events = new ArrayList<>();
        if (!engine.expireTimedOutOffers(table, context, nextRevision, events)) {
            return;
        }
        Map<Integer, Long> deltas = QaTaizhouRoundEngine.deltas(table);
        if (goldWalletSettlementService != null && !deltas.isEmpty()) {
            goldWalletSettlementService.settle(room, seats, deltas);
        }
        persister.persistServerAdvance(
                session,
                new QaRoundCoordinator.QaRoundCommandOutcome(
                        QaTaizhouRoundEngine.phaseOf(table),
                        table.roundNumber,
                        nextRevision,
                        engine.sessionState(table, context),
                        events,
                        deltas,
                        seats),
                now);
    }

    void sweepAllPlaying() {
        for (UUID sessionId : sessionRepository.findPlayingIds()) {
            try {
                expireDueOffer(sessionId);
            } catch (RuntimeException exception) {
                log.warn("round timeout sweep failed for session {}", sessionId, exception);
            }
        }
    }

    private boolean enabled() {
        return qaBotService != null && qaBotService.enabled();
    }

    private static String gameRuleDisplay(GameRoomEntity room) {
        if (!room.getGameRuleDisplay().isBlank()) {
            return room.getGameRuleDisplay();
        }
        return TaizhouMahjongRuleDisplay.render(
                room.getGameRule(), room.getPlayerCount(), room.getPlayCount(), room.getPayType());
    }
}
