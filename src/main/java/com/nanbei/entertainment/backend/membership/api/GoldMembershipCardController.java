package com.nanbei.entertainment.backend.membership.api;

import com.nanbei.entertainment.backend.membership.application.GoldMembershipCardService;
import com.nanbei.entertainment.backend.membership.application.GoldMembershipCardStatus;
import com.nanbei.entertainment.backend.membership.application.GoldMembershipCardsResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/membership/gold-cards")
public class GoldMembershipCardController {
    private final GoldMembershipCardService service;

    public GoldMembershipCardController(GoldMembershipCardService service) {
        this.service = service;
    }

    @GetMapping
    GoldMembershipCardsResponse cards(@AuthenticationPrincipal Jwt jwt) {
        return service.cards(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{productCode}/claim")
    GoldMembershipCardStatus claim(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String productCode) {
        return service.claim(UUID.fromString(jwt.getSubject()), productCode);
    }
}
