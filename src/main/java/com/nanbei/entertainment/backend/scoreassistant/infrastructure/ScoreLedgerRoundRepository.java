package com.nanbei.entertainment.backend.scoreassistant.infrastructure;

import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerRoundEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreLedgerRoundRepository
        extends JpaRepository<ScoreLedgerRoundEntity, UUID> {
    @Query("""
            select distinct round
            from ScoreLedgerRoundEntity round
            join fetch round.scores score
            join fetch score.player
            where round.ledger.id = :ledgerId
            order by round.roundNumber asc
            """)
    List<ScoreLedgerRoundEntity> findDetailedByLedgerId(
            @Param("ledgerId") UUID ledgerId);
}
