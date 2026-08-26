package com.nanbei.entertainment.backend.scoreassistant.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerPlayerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerRoundEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRepository;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRoundRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreLedgerCommandService {
    private final ScoreLedgerRepository ledgerRepository;
    private final ScoreLedgerRoundRepository roundRepository;
    private final Clock clock;

    @Autowired
    public ScoreLedgerCommandService(
            ScoreLedgerRepository ledgerRepository,
            ScoreLedgerRoundRepository roundRepository) {
        this(ledgerRepository, roundRepository, Clock.systemUTC());
    }

    ScoreLedgerCommandService(ScoreLedgerRepository ledgerRepository, Clock clock) {
        this(ledgerRepository, null, clock);
    }

    ScoreLedgerCommandService(
            ScoreLedgerRepository ledgerRepository,
            ScoreLedgerRoundRepository roundRepository,
            Clock clock) {
        this.ledgerRepository = ledgerRepository;
        this.roundRepository = roundRepository;
        this.clock = clock;
    }

    @Transactional
    public ScoreLedgerDetailResponse create(
            UUID ownerUserId, List<CreateScorePlayer> requestedPlayers) {
        List<ScoreLedgerEntity.NamedPlayer> players = validatePlayers(requestedPlayers);
        ScoreLedgerEntity ledger =
                new ScoreLedgerEntity(ownerUserId, players, clock.instant());
        return ScoreLedgerDetailResponse.created(ledgerRepository.save(ledger));
    }

    @Transactional
    public ScoreRoundResponse recordRound(
            UUID ownerUserId, UUID ledgerId, List<RecordScore> requestedScores) {
        ScoreLedgerEntity ledger = ownedForUpdate(ownerUserId, ledgerId);
        if (ledger.getStatus() != ScoreLedgerStatus.IN_PROGRESS || ledger.getRoundCount() >= 99) {
            throw new ApiException(
                    ErrorCode.SCORE_LEDGER_ILLEGAL_STATE, "账本已结束或已达到99局");
        }
        Map<ScoreLedgerPlayerEntity, Long> deltas = validateScores(ledger, requestedScores);
        Map<ScoreLedgerPlayerEntity, Long> totals = new HashMap<>();
        try {
            for (Map.Entry<ScoreLedgerPlayerEntity, Long> entry : deltas.entrySet()) {
                totals.put(
                        entry.getKey(),
                        Math.addExact(entry.getKey().getTotalScore(), entry.getValue()));
            }
        } catch (ArithmeticException exception) {
            throw invalid("分数超出允许范围");
        }

        ScoreLedgerRoundEntity round = new ScoreLedgerRoundEntity(
                ledger,
                ledger.getRoundCount() + 1,
                clock.instant(),
                deltas,
                totals);
        totals.forEach(ScoreLedgerPlayerEntity::setTotalScore);
        ledger.incrementRoundCount();
        return ScoreRoundResponse.from(roundRepository.save(round));
    }

    @Transactional
    public ScoreLedgerStateResponse end(UUID ownerUserId, UUID ledgerId) {
        ScoreLedgerEntity ledger = ownedForUpdate(ownerUserId, ledgerId);
        if (ledger.getStatus() != ScoreLedgerStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.SCORE_LEDGER_ILLEGAL_STATE, "账本已结束");
        }
        ledger.end(clock.instant());
        return ScoreLedgerStateResponse.from(ledger);
    }

    @Transactional
    public ScoreLedgerStateResponse setFavorite(
            UUID ownerUserId, UUID ledgerId, boolean favorite) {
        ScoreLedgerEntity ledger = ownedForUpdate(ownerUserId, ledgerId);
        ledger.setFavorite(favorite);
        return ScoreLedgerStateResponse.from(ledger);
    }

    @Transactional
    public ScoreLedgerDeleteResponse delete(UUID ownerUserId, UUID ledgerId) {
        ScoreLedgerEntity ledger = ownedForUpdate(ownerUserId, ledgerId);
        Instant deletedAt = clock.instant();
        ledger.delete(deletedAt);
        return new ScoreLedgerDeleteResponse(ledger.getId(), deletedAt);
    }

    private ScoreLedgerEntity ownedForUpdate(UUID ownerUserId, UUID ledgerId) {
        return ledgerRepository.findOwnedForUpdate(ledgerId, ownerUserId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.SCORE_LEDGER_NOT_FOUND, "计分账本不存在"));
    }

    private static Map<ScoreLedgerPlayerEntity, Long> validateScores(
            ScoreLedgerEntity ledger, List<RecordScore> requestedScores) {
        if (requestedScores == null
                || requestedScores.size() != ledger.getPlayers().size()) {
            throw invalid("必须提交全部玩家的本局分数");
        }
        Map<UUID, ScoreLedgerPlayerEntity> players = new HashMap<>();
        ledger.getPlayers().forEach(player -> players.put(player.getId(), player));
        Map<ScoreLedgerPlayerEntity, Long> deltas = new HashMap<>();
        long total = 0;
        try {
            for (RecordScore requested : requestedScores) {
                ScoreLedgerPlayerEntity player = requested == null
                        ? null
                        : players.get(requested.playerId());
                if (player == null || deltas.put(player, requested.scoreDelta()) != null) {
                    throw invalid("玩家分数不完整或重复");
                }
                total = Math.addExact(total, requested.scoreDelta());
            }
        } catch (ArithmeticException exception) {
            throw invalid("分数超出允许范围");
        }
        if (total != 0) {
            throw invalid("每局所有玩家分数之和必须为零");
        }
        return deltas;
    }

    private static List<ScoreLedgerEntity.NamedPlayer> validatePlayers(
            List<CreateScorePlayer> requested) {
        if (requested == null || requested.size() < 2 || requested.size() > 6) {
            throw invalid("计分人数必须为2至6人");
        }
        Set<String> names = new HashSet<>();
        int ownerCount = 0;
        java.util.ArrayList<ScoreLedgerEntity.NamedPlayer> players = new java.util.ArrayList<>();
        for (CreateScorePlayer requestedPlayer : requested) {
            if (requestedPlayer == null || requestedPlayer.name() == null) {
                throw invalid("玩家名称不能为空");
            }
            String name = requestedPlayer.name().strip();
            if (name.isEmpty() || name.length() > 40 || !names.add(name)) {
                throw invalid("玩家名称不正确或重复");
            }
            if (requestedPlayer.ownerPlayer()) {
                ownerCount++;
            }
            players.add(new ScoreLedgerEntity.NamedPlayer(name, requestedPlayer.ownerPlayer()));
        }
        if (ownerCount != 1) {
            throw invalid("必须且只能指定一个本人玩家");
        }
        return List.copyOf(players);
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.SCORE_LEDGER_INVALID, message);
    }
}
