package com.nanbei.entertainment.backend.fortune.infrastructure;

import com.nanbei.entertainment.backend.fortune.domain.FortuneStateEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FortuneStateRepository extends JpaRepository<FortuneStateEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from FortuneStateEntity state where state.userId = :userId")
    Optional<FortuneStateEntity> findLockedByUserId(@Param("userId") UUID userId);
}
