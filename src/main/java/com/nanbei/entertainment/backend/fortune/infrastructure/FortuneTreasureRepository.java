package com.nanbei.entertainment.backend.fortune.infrastructure;

import com.nanbei.entertainment.backend.fortune.domain.FortuneTreasureEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface FortuneTreasureRepository
        extends JpaRepository<FortuneTreasureEntity, UUID> {
    List<FortuneTreasureEntity> findByUserIdOrderByTreasureCode(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FortuneTreasureEntity> findByUserIdAndTreasureCode(
            UUID userId, String treasureCode);
}
