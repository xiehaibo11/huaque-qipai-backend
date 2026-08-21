package com.nanbei.entertainment.backend.region.infrastructure;

import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionLobbyRepository
        extends JpaRepository<RegionLobbyEntity, Long> {
    List<RegionLobbyEntity> findByEnabledTrueOrderBySortOrderAsc();

    Optional<RegionLobbyEntity> findByLobbyIdAndEnabledTrue(long lobbyId);

    Optional<RegionLobbyEntity>
            findFirstByDefaultLobbyTrueAndEnabledTrueOrderBySortOrderAsc();
}
