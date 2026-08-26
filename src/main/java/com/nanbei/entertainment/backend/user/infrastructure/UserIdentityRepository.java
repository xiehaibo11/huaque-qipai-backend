package com.nanbei.entertainment.backend.user.infrastructure;

import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {
    Optional<UserIdentityEntity> findByProviderAndProviderSubject(
            IdentityProvider provider, String providerSubject);

    List<UserIdentityEntity> findAllByProviderAndProviderSubjectIn(
            IdentityProvider provider, List<String> providerSubjects);

    List<UserIdentityEntity> findByUser_IdOrderByCreatedAtAsc(UUID userId);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireIdentityLock(@Param("lockKey") String lockKey);
}
