package com.nanbei.entertainment.backend.scoreassistant.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerPlayerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRepository;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRoundRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreLedgerQueryService {
    private static final ZoneId ORIGINAL_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final ScoreLedgerRepository ledgerRepository;
    private final ScoreLedgerRoundRepository roundRepository;
    private final Clock clock;

    @Autowired
    public ScoreLedgerQueryService(
            ScoreLedgerRepository ledgerRepository,
            ScoreLedgerRoundRepository roundRepository) {
        this(ledgerRepository, roundRepository, Clock.systemUTC());
    }

    ScoreLedgerQueryService(
            ScoreLedgerRepository ledgerRepository,
            ScoreLedgerRoundRepository roundRepository,
            Clock clock) {
        this.ledgerRepository = ledgerRepository;
        this.roundRepository = roundRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ScoreLedgerListResponse inProgress(UUID ownerUserId) {
        List<ScoreLedgerSummary> ledgers = ledgerRepository
                .findByOwnerUserIdAndStatusAndDeletedAtIsNullOrderByStartedAtDesc(
                        ownerUserId, ScoreLedgerStatus.IN_PROGRESS)
                .stream()
                .map(ScoreLedgerSummary::from)
                .toList();
        return new ScoreLedgerListResponse(ledgers);
    }

    @Transactional(readOnly = true)
    public ScoreLedgerDetailResponse detail(UUID ownerUserId, UUID ledgerId) {
        ScoreLedgerEntity ledger = ledgerRepository.findOwned(ledgerId, ownerUserId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.SCORE_LEDGER_NOT_FOUND, "计分账本不存在"));
        return ScoreLedgerDetailResponse.from(
                ledger, roundRepository.findDetailedByLedgerId(ledgerId));
    }

    @Transactional(readOnly = true)
    public ScoreLedgerHistoryPage history(
            UUID ownerUserId, int requestedPage, int requestedSize) {
        if (requestedPage < 1 || requestedSize < 1 || requestedSize > 100) {
            throw new ApiException(
                    ErrorCode.SCORE_LEDGER_INVALID, "页码必须大于0，每页数量必须为1至100");
        }
        Page<ScoreLedgerEntity> result = ledgerRepository.findHistory(
                ownerUserId,
                ScoreLedgerStatus.ENDED,
                PageRequest.of(requestedPage - 1, requestedSize));
        return new ScoreLedgerHistoryPage(
                requestedPage,
                requestedSize,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream().map(ScoreLedgerSummary::from).toList());
    }

    @Transactional(readOnly = true)
    public ScoreLedgerMonthlyStatistics monthly(UUID ownerUserId, YearMonth requestedMonth) {
        YearMonth month = requestedMonth == null
                ? YearMonth.now(clock.withZone(ORIGINAL_TIME_ZONE))
                : requestedMonth;
        Instant start = month.atDay(1).atStartOfDay(ORIGINAL_TIME_ZONE).toInstant();
        Instant end = month.plusMonths(1)
                .atDay(1)
                .atStartOfDay(ORIGINAL_TIME_ZONE)
                .toInstant();
        return statistics(
                month, ledgerRepository.findEndedInPeriod(ownerUserId, start, end));
    }

    private static ScoreLedgerMonthlyStatistics statistics(
            YearMonth month, List<ScoreLedgerEntity> ledgers) {
        int wins = 0;
        long totalScore = 0;
        long winScore = 0;
        Map<String, Long> companionScores = new HashMap<>();
        for (ScoreLedgerEntity ledger : ledgers) {
            ScoreLedgerPlayerEntity self = ledger.getPlayers().stream()
                    .filter(ScoreLedgerPlayerEntity::isOwnerPlayer)
                    .findFirst()
                    .orElseThrow();
            long score = self.getTotalScore();
            totalScore += score;
            if (score > 0) {
                wins++;
                winScore += score;
            }
            ledger.getPlayers().stream()
                    .filter(player -> !player.isOwnerPlayer())
                    .forEach(player -> companionScores.merge(
                            player.getDisplayName(), player.getTotalScore(), Long::sum));
        }
        String best = extreme(companionScores, Comparator.comparingLong(Map.Entry::getValue));
        String worst = extreme(
                companionScores, Comparator.comparingLong(Map.Entry<String, Long>::getValue).reversed());
        return new ScoreLedgerMonthlyStatistics(
                month,
                ledgers.size(),
                wins,
                ledgers.size() - wins,
                totalScore,
                winScore,
                totalScore - winScore,
                best,
                worst);
    }

    private static String extreme(
            Map<String, Long> scores,
            Comparator<Map.Entry<String, Long>> comparator) {
        return scores.entrySet().stream()
                .max(comparator.thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
