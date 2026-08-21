package com.nanbei.entertainment.backend.region.infrastructure;

import com.nanbei.entertainment.backend.region.domain.UserRegionSelectionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRegionSelectionRepository
        extends JpaRepository<UserRegionSelectionEntity, UUID> {}
