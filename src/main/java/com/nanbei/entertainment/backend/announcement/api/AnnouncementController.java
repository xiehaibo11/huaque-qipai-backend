package com.nanbei.entertainment.backend.announcement.api;

import com.nanbei.entertainment.backend.announcement.application.AnnouncementDetailResponse;
import com.nanbei.entertainment.backend.announcement.application.AnnouncementListResponse;
import com.nanbei.entertainment.backend.announcement.application.AnnouncementReadResponse;
import com.nanbei.entertainment.backend.announcement.application.AnnouncementService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {
    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    AnnouncementListResponse list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    @GetMapping("/{announcementId}")
    AnnouncementDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long announcementId) {
        return service.detail(userId(jwt), announcementId);
    }

    @PostMapping("/{announcementId}/read")
    AnnouncementReadResponse markRead(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long announcementId) {
        return service.markRead(userId(jwt), announcementId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
