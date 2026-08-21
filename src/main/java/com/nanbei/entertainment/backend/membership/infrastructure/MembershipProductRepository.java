package com.nanbei.entertainment.backend.membership.infrastructure;

import com.nanbei.entertainment.backend.membership.domain.MembershipProductEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipProductRepository
        extends JpaRepository<MembershipProductEntity, UUID> {
    List<MembershipProductEntity> findByActiveTrueOrderBySortOrderAsc();

    Optional<MembershipProductEntity> findByProductIdAndActiveTrue(UUID productId);
}
