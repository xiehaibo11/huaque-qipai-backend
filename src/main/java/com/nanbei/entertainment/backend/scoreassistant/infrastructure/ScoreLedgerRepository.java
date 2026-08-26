package com.nanbei.entertainment.backend.scoreassistant.infrastructure;

import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreLedgerRepository extends JpaRepository<ScoreLedgerEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct ledger
            from ScoreLedgerEntity ledger
            join fetch ledger.players
            where ledger.id = :ledgerId
              and ledger.ownerUserId = :ownerUserId
              and ledger.deletedAt is null
            """)
    Optional<ScoreLedgerEntity> findOwnedForUpdate(
            @Param("ledgerId") UUID ledgerId,
            @Param("ownerUserId") UUID ownerUserId);

    @Query("""
            select distinct ledger
            from ScoreLedgerEntity ledger
            join fetch ledger.players
            where ledger.id = :ledgerId
              and ledger.ownerUserId = :ownerUserId
              and ledger.deletedAt is null
            """)
    Optional<ScoreLedgerEntity> findOwned(
            @Param("ledgerId") UUID ledgerId,
            @Param("ownerUserId") UUID ownerUserId);

    List<ScoreLedgerEntity>
            findByOwnerUserIdAndStatusAndDeletedAtIsNullOrderByStartedAtDesc(
                    UUID ownerUserId, ScoreLedgerStatus status);

    @Query("""
            select ledger
            from ScoreLedgerEntity ledger
            where ledger.ownerUserId = :ownerUserId
              and ledger.status = :status
              and ledger.deletedAt is null
            order by ledger.endedAt desc, ledger.id desc
            """)
    Page<ScoreLedgerEntity> findHistory(
            @Param("ownerUserId") UUID ownerUserId,
            @Param("status") ScoreLedgerStatus status,
            Pageable pageable);

    @Query("""
            select distinct ledger
            from ScoreLedgerEntity ledger
            join fetch ledger.players
            where ledger.ownerUserId = :ownerUserId
              and ledger.status = 'ENDED'
              and ledger.deletedAt is null
              and ledger.endedAt >= :startInclusive
              and ledger.endedAt < :endExclusive
            order by ledger.endedAt asc
            """)
    List<ScoreLedgerEntity> findEndedInPeriod(
            @Param("ownerUserId") UUID ownerUserId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);
}
