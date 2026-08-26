package com.nanbei.entertainment.backend.announcement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.announcement.application.AnnouncementDetailResponse;
import com.nanbei.entertainment.backend.announcement.application.AnnouncementListResponse;
import com.nanbei.entertainment.backend.announcement.application.AnnouncementReadResponse;
import com.nanbei.entertainment.backend.announcement.application.AnnouncementService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AnnouncementControllerTest {
    @Mock AnnouncementService service;

    @Test
    void listAndDetailUseOnlyTheAuthenticatedJwtSubject() {
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
        AnnouncementListResponse list = new AnnouncementListResponse(900023L, List.of());
        AnnouncementDetailResponse detail = new AnnouncementDetailResponse(
                7L,
                "公告",
                "副标题",
                "正文",
                null,
                900023L,
                1,
                null,
                null,
                3,
                true,
                Instant.parse("2026-08-24T12:00:00Z"));
        when(service.list(userId)).thenReturn(list);
        when(service.detail(userId, 7L)).thenReturn(detail);
        AnnouncementReadResponse read = new AnnouncementReadResponse(
                7L, 3, true, Instant.parse("2026-08-24T12:00:00Z"));
        when(service.markRead(userId, 7L)).thenReturn(read);
        AnnouncementController controller = new AnnouncementController(service);

        assertThat(controller.list(jwt)).isSameAs(list);
        assertThat(controller.detail(jwt, 7L)).isSameAs(detail);
        assertThat(controller.markRead(jwt, 7L)).isSameAs(read);
        verify(service).list(userId);
        verify(service).detail(userId, 7L);
        verify(service).markRead(userId, 7L);
    }
}
