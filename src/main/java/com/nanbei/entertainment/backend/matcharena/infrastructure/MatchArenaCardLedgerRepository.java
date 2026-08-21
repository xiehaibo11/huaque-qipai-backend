package com.nanbei.entertainment.backend.matcharena.infrastructure;

import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaCardLedgerEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchArenaCardLedgerRepository
        extends JpaRepository<MatchArenaCardLedgerEntity, UUID> {}
