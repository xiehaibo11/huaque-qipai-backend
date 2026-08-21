package com.nanbei.entertainment.backend.realname.infrastructure;

import com.nanbei.entertainment.backend.realname.domain.RealNameFailedAttemptEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RealNameFailedAttemptRepository
        extends JpaRepository<RealNameFailedAttemptEntity, UUID> {
    long countByUserIdAndFailedAtAfter(UUID userId, Instant failedAfter);
}
