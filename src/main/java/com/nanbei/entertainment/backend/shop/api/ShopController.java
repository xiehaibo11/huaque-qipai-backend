package com.nanbei.entertainment.backend.shop.api;

import com.nanbei.entertainment.backend.shop.application.ShopCatalogResponse;
import com.nanbei.entertainment.backend.shop.application.ShopCatalogService;
import com.nanbei.entertainment.backend.shop.application.ShopInventoryResponse;
import com.nanbei.entertainment.backend.shop.application.ShopInventoryService;
import com.nanbei.entertainment.backend.shop.application.ShopPurchaseResponse;
import com.nanbei.entertainment.backend.shop.application.ShopPurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {
    private final ShopCatalogService catalogService;
    private final ShopPurchaseService purchaseService;
    private final ShopInventoryService inventoryService;

    public ShopController(
            ShopCatalogService catalogService,
            ShopPurchaseService purchaseService,
            ShopInventoryService inventoryService) {
        this.catalogService = catalogService;
        this.purchaseService = purchaseService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/catalog")
    ShopCatalogResponse catalog(@AuthenticationPrincipal Jwt jwt) {
        return catalogService.load(userId(jwt));
    }

    @PostMapping("/exchanges")
    ShopPurchaseResponse exchange(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExchangeRequest request) {
        return purchaseService.exchange(
                userId(jwt), request.productCode(), idempotencyKey);
    }

    @GetMapping("/inventory")
    List<ShopInventoryResponse> inventory(@AuthenticationPrincipal Jwt jwt) {
        return inventoryService.list(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record ExchangeRequest(@NotBlank String productCode) {}
}
