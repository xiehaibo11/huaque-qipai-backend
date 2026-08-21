package com.nanbei.entertainment.backend.membership.api;

import com.nanbei.entertainment.backend.membership.application.MembershipDailyGiftService;
import com.nanbei.entertainment.backend.membership.application.MembershipDailyGiftStatus;
import com.nanbei.entertainment.backend.membership.application.MembershipGoldStatisticsService;
import com.nanbei.entertainment.backend.membership.application.MembershipGoldStatisticsStatus;
import com.nanbei.entertainment.backend.membership.application.MembershipNoticeResponse;
import com.nanbei.entertainment.backend.membership.application.MembershipNoticeService;
import com.nanbei.entertainment.backend.membership.application.MembershipProductResponse;
import com.nanbei.entertainment.backend.membership.application.MembershipProductService;
import com.nanbei.entertainment.backend.membership.application.MembershipStatus;
import com.nanbei.entertainment.backend.membership.application.MembershipStatusService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {
    private final MembershipDailyGiftService dailyGiftService;
    private final MembershipGoldStatisticsService goldStatisticsService;
    private final MembershipNoticeService membershipNoticeService;
    private final MembershipProductService productService;
    private final MembershipStatusService statusService;

    public MembershipController(
            MembershipDailyGiftService dailyGiftService,
            MembershipGoldStatisticsService goldStatisticsService,
            MembershipNoticeService membershipNoticeService,
            MembershipProductService productService,
            MembershipStatusService statusService) {
        this.dailyGiftService = dailyGiftService;
        this.goldStatisticsService = goldStatisticsService;
        this.membershipNoticeService = membershipNoticeService;
        this.productService = productService;
        this.statusService = statusService;
    }

    @GetMapping("/products")
    List<MembershipProductResponse> products() {
        return productService.listProducts();
    }

    @GetMapping("/notice")
    MembershipNoticeResponse notice() {
        return membershipNoticeService.current();
    }

    @GetMapping("/status")
    MembershipStatus status(@AuthenticationPrincipal Jwt jwt) {
        return statusService.status(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/daily-gift")
    MembershipDailyGiftStatus dailyGift(@AuthenticationPrincipal Jwt jwt) {
        return dailyGiftService.status(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/gold-statistics")
    MembershipGoldStatisticsStatus goldStatistics(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") long gameId) {
        return goldStatisticsService.status(UUID.fromString(jwt.getSubject()), gameId);
    }

    @PostMapping("/daily-gift/claim")
    MembershipDailyGiftStatus claimDailyGift(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClaimDailyGiftRequest request) {
        return dailyGiftService.claim(
                UUID.fromString(jwt.getSubject()), request.giftId());
    }

    public record ClaimDailyGiftRequest(@Min(1) @Max(2) int giftId) {}
}
