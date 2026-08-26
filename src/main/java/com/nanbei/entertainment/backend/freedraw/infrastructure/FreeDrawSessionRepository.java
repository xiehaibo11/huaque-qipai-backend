package com.nanbei.entertainment.backend.freedraw.infrastructure;

import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawSessionEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreeDrawSessionRepository extends JpaRepository<FreeDrawSessionEntity, UUID> {
    long countByUserIdAndActivityIdAndDrawDateAndStatus(
            UUID userId, UUID activityId, LocalDate drawDate, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from FreeDrawSessionEntity session where session.id = :id")
    Optional<FreeDrawSessionEntity> findLockedById(@Param("id") UUID id);
}
