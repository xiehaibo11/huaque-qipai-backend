package com.nanbei.entertainment.backend.user.infrastructure;

import com.nanbei.entertainment.backend.user.domain.UserEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {}
