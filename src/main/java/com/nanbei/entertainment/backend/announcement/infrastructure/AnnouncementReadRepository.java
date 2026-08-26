package com.nanbei.entertainment.backend.announcement.infrastructure;

import com.nanbei.entertainment.backend.announcement.domain.AnnouncementReadEntity;
import com.nanbei.entertainment.backend.announcement.domain.AnnouncementReadId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementReadRepository
        extends JpaRepository<AnnouncementReadEntity, AnnouncementReadId> {
    List<AnnouncementReadEntity> findByIdUserIdAndIdAnnouncementIdIn(
            UUID userId, Collection<Long> announcementIds);
}
