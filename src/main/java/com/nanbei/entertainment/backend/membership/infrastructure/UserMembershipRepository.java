package com.nanbei.entertainment.backend.membership.infrastructure;

import com.nanbei.entertainment.backend.membership.domain.UserMembershipEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMembershipRepository
        extends JpaRepository<UserMembershipEntity, UUID> {}
