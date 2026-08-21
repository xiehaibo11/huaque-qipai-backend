package com.nanbei.entertainment.backend.mail.infrastructure;

import com.nanbei.entertainment.backend.mail.domain.MailEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailRepository extends JpaRepository<MailEntity, Long> {
    @Query(
            "select m from MailEntity m where m.userId = :userId and m.deletedAt is null"
                    + " and (m.expireAt is null or m.expireAt > :now)"
                    + " order by m.sendAt desc, m.id desc")
    List<MailEntity> findVisible(@Param("userId") UUID userId, @Param("now") Instant now);

    Optional<MailEntity> findByIdAndUserId(Long id, UUID userId);

    List<MailEntity> findByUserIdAndIdIn(UUID userId, Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select m from MailEntity m where m.userId = :userId and m.id in :ids"
                    + " order by m.sendAt desc, m.id desc")
    List<MailEntity> findLockedByUserIdAndIdIn(
            @Param("userId") UUID userId, @Param("ids") Collection<Long> ids);

    @Modifying
    @Query(
            "update MailEntity m set m.readAt = :now where m.userId = :userId"
                    + " and m.readAt is null and m.deletedAt is null"
                    + " and (m.expireAt is null or m.expireAt > :now)")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
