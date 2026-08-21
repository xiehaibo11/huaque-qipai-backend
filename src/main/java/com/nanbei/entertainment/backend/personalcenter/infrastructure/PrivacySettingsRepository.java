package com.nanbei.entertainment.backend.personalcenter.infrastructure;

import com.nanbei.entertainment.backend.personalcenter.domain.PrivacySettingsEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacySettingsRepository
        extends JpaRepository<PrivacySettingsEntity, UUID> {}
