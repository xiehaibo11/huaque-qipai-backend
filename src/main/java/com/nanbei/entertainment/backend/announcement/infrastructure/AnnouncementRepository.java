package com.nanbei.entertainment.backend.announcement.infrastructure;

import com.nanbei.entertainment.backend.announcement.domain.AnnouncementEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, Long> {
    List<AnnouncementEntity> findAllByOrderBySortOrderAscIdAsc();
}
