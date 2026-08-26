package com.nanbei.entertainment.backend.wechatpush.infrastructure;

import com.nanbei.entertainment.backend.wechatpush.domain.WeChatPushEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeChatPushEventRepository
        extends JpaRepository<WeChatPushEventEntity, UUID> {
    boolean existsByFingerprint(String fingerprint);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireEventLock(@Param("lockKey") String lockKey);
}
