package com.nanbei.entertainment.backend.user.infrastructure;

import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {
    Optional<UserIdentityEntity> findByProviderAndProviderSubject(
            IdentityProvider provider, String providerSubject);

    List<UserIdentityEntity> findByUser_IdOrderByCreatedAtAsc(UUID userId);
}
