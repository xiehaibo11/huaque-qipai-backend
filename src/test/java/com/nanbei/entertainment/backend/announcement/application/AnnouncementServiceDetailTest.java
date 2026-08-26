package com.nanbei.entertainment.backend.announcement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.announcement.domain.AnnouncementEntity;
import com.nanbei.entertainment.backend.announcement.domain.AnnouncementReadEntity;
import com.nanbei.entertainment.backend.announcement.domain.AnnouncementReadId;
import com.nanbei.entertainment.backend.announcement.infrastructure.AnnouncementReadRepository;
import com.nanbei.entertainment.backend.announcement.infrastructure.AnnouncementRepository;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import com.nanbei.entertainment.backend.region.domain.UserRegionSelectionEntity;
import com.nanbei.entertainment.backend.region.infrastructure.RegionLobbyRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceDetailTest {
    private static final UUID USER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final long TAIZHOU = 900023L;

    @Mock AnnouncementRepository announcementRepository;
    @Mock AnnouncementReadRepository readRepository;
    @Mock UserRegionSelectionRepository selectionRepository;
    @Mock RegionLobbyRepository lobbyRepository;

    AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(
                announcementRepository,
                readRepository,
                selectionRepository,
                lobbyRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(selectionRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserRegionSelectionEntity(USER_ID, TAIZHOU)));
        when(lobbyRepository.findByLobbyIdAndEnabledTrue(TAIZHOU))
                .thenReturn(Optional.of(new RegionLobbyEntity(
                        TAIZHOU, "331000", "台州", 1, true, true)));
    }

    @Test
    void detailMarksTheCurrentAnnouncementVersionRead() {
        AnnouncementEntity announcement = announcement(7L, TAIZHOU, true, 3);
        AnnouncementReadEntity oldRead =
                new AnnouncementReadEntity(USER_ID, 7L, 2, NOW.minusSeconds(60));
        when(announcementRepository.findById(7L)).thenReturn(Optional.of(announcement));
        when(readRepository.findById(new AnnouncementReadId(USER_ID, 7L)))
                .thenReturn(Optional.of(oldRead));

        AnnouncementDetailResponse detail = service.detail(USER_ID, 7L);

        assertThat(detail.announcementId()).isEqualTo(7L);
        assertThat(detail.title()).isEqualTo("公告7");
        assertThat(detail.bodyText()).isEqualTo("正文7");
        assertThat(detail.pageUrl()).isNull();
        assertThat(detail.version()).isEqualTo(3);
        assertThat(detail.read()).isTrue();
        assertThat(detail.readAt()).isEqualTo(NOW);
        assertThat(oldRead.getAnnouncementVersion()).isEqualTo(3);
        assertThat(oldRead.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void detailHidesAnnouncementsOutsideTheUsersLobby() {
        when(announcementRepository.findById(7L))
                .thenReturn(Optional.of(announcement(7L, 900038L, true, 1)));

        assertThatThrownBy(() -> service.detail(USER_ID, 7L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
    }

    @Test
    void detailHidesDisabledAnnouncements() {
        when(announcementRepository.findById(7L))
                .thenReturn(Optional.of(announcement(7L, TAIZHOU, false, 1)));

        assertThatThrownBy(() -> service.detail(USER_ID, 7L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
    }

    @Test
    void explicitReadMarksTheVisibleAnnouncementVersion() {
        AnnouncementEntity announcement = announcement(8L, TAIZHOU, true, 6);
        when(announcementRepository.findById(8L)).thenReturn(Optional.of(announcement));
        when(readRepository.findById(new AnnouncementReadId(USER_ID, 8L)))
                .thenReturn(Optional.empty());

        AnnouncementReadResponse response = service.markRead(USER_ID, 8L);

        assertThat(response.announcementId()).isEqualTo(8L);
        assertThat(response.version()).isEqualTo(6);
        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isEqualTo(NOW);
    }

    private static AnnouncementEntity announcement(
            long id, Long lobbyId, boolean enabled, long version) {
        AnnouncementEntity entity = new AnnouncementEntity(
                "公告" + id,
                "副标题" + id,
                "正文" + id,
                null,
                lobbyId,
                1,
                enabled,
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                version);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
