package com.nanbei.entertainment.backend.freedraw.infrastructure;

import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawActivityEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreeDrawActivityRepository extends JpaRepository<FreeDrawActivityEntity, UUID> {
    Optional<FreeDrawActivityEntity> findFirstByEnabledTrueOrderByActivityCodeAsc();
}
