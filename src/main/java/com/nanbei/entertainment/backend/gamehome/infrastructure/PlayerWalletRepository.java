package com.nanbei.entertainment.backend.gamehome.infrastructure;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerWalletRepository
        extends JpaRepository<PlayerWalletEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from PlayerWalletEntity wallet where wallet.userId = :userId")
    Optional<PlayerWalletEntity> findLockedByUserId(@Param("userId") UUID userId);
}
