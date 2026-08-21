package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.domain.OtpChallengeEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository
        extends JpaRepository<OtpChallengeEntity, UUID> {
    Optional<OtpChallengeEntity>
            findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc(
                    String phoneNumber);

    long countByPhoneNumberAndCreatedAtAfter(
            String phoneNumber, Instant after);
}
