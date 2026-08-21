package com.nanbei.entertainment.backend.region.api;

import com.nanbei.entertainment.backend.region.application.RegionCatalog;
import com.nanbei.entertainment.backend.region.application.RegionCatalogService;
import com.nanbei.entertainment.backend.region.application.RegionSelection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {
    private final RegionCatalogService regionCatalogService;

    public RegionController(RegionCatalogService regionCatalogService) {
        this.regionCatalogService = regionCatalogService;
    }

    @GetMapping
    RegionCatalog catalog() {
        return regionCatalogService.loadCatalog();
    }

    @PutMapping("/selection")
    RegionSelection select(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SelectionRequest request) {
        return regionCatalogService.saveSelection(
                UUID.fromString(jwt.getSubject()), request.lobbyId());
    }

    public record SelectionRequest(@Positive long lobbyId) {}
}
