package com.nanbei.entertainment.backend.personalcenter.infrastructure;

import com.nanbei.entertainment.backend.personalcenter.domain.UserFeedbackEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeedbackRepository
        extends JpaRepository<UserFeedbackEntity, UUID> {
    List<UserFeedbackEntity>
            findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
