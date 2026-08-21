package com.nanbei.entertainment.backend.shop.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopControllerContractTest {
    @Test
    void exposesProtectedCatalogExchangeAndInventoryRoutes() throws Exception {
        Path source =
                Path.of("src/main/java/com/nanbei/entertainment/backend/shop/api/ShopController.java");
        String java = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);

        assertThat(java).contains("@RequestMapping(\"/api/v1/shop\")");
        assertThat(java).contains("@GetMapping(\"/catalog\")");
        assertThat(java).contains("@PostMapping(\"/exchanges\")");
        assertThat(java).contains("@GetMapping(\"/inventory\")");
        assertThat(java).contains("@RequestHeader(\"Idempotency-Key\")");
        assertThat(java).contains("@AuthenticationPrincipal Jwt jwt");
    }
}
