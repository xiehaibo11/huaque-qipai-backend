package com.nanbei.entertainment.backend.timeloginact.infrastructure;

import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginActivityEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeLoginActivityRepository
        extends JpaRepository<TimeLoginActivityEntity, UUID> {
    Optional<TimeLoginActivityEntity> findFirstByEnabledTrueOrderByActivityCodeAsc();
}
