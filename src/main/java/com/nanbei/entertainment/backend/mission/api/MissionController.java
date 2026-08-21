package com.nanbei.entertainment.backend.mission.api;

import com.nanbei.entertainment.backend.mission.application.MissionClaimService;
import com.nanbei.entertainment.backend.mission.application.MissionQueryService;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionCatalogResponse;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionPageStatus;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/missions")
public class MissionController {
    private final MissionQueryService queryService;
    private final MissionClaimService claimService;

    public MissionController(
            MissionQueryService queryService, MissionClaimService claimService) {
        this.queryService = queryService;
        this.claimService = claimService;
    }

    @GetMapping
    MissionCatalogResponse catalog(@AuthenticationPrincipal Jwt jwt) {
        return queryService.catalog(userId(jwt));
    }

    /**
     * 原版 initTabs 打开时选中 pageList 的第一个页签，客户端此时还不知道有哪些页签，
     * 所以给一个不带页面编码的入口，由服务端按 displayOrder 决定首屏页。
     */
    @GetMapping("/page")
    MissionPageStatus firstPage(@AuthenticationPrincipal Jwt jwt) {
        return queryService.firstPage(userId(jwt));
    }

    @GetMapping("/pages/{pageCode}")
    MissionPageStatus page(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("pageCode") String pageCode) {
        return queryService.page(userId(jwt), pageCode);
    }

    @PostMapping("/tasks/{taskCode}/claim")
    MissionPageStatus claimTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("taskCode") String taskCode,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String key) {
        return claimService.claimTask(userId(jwt), taskCode, key);
    }

    @PostMapping("/pages/{pageCode}/milestones/{target}/claim")
    MissionPageStatus claimMilestone(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("pageCode") String pageCode,
            @PathVariable("target") long target,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String key) {
        return claimService.claimMilestone(userId(jwt), pageCode, target, key);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
