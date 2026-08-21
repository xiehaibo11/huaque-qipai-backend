package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.shop.infrastructure.ShopInventoryItemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopInventoryService {
    private final ShopInventoryItemRepository repository;

    public ShopInventoryService(ShopInventoryItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ShopInventoryResponse> list(UUID userId) {
        return repository.findByUserIdOrderByItemCodeAsc(userId).stream()
                .map(ShopInventoryResponse::from)
                .toList();
    }
}
