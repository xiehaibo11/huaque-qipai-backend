package com.nanbei.entertainment.backend.announcement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.announcement.domain.AnnouncementEntity;
import com.nanbei.entertainment.backend.announcement.domain.AnnouncementReadEntity;
import com.nanbei.entertainment.backend.announcement.infrastructure.AnnouncementReadRepository;
import com.nanbei.entertainment.backend.announcement.infrastructure.AnnouncementRepository;
import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import com.nanbei.entertainment.backend.region.domain.UserRegionSelectionEntity;
import com.nanbei.entertainment.backend.region.infrastructure.RegionLobbyRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceListTest {
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
    }

    @Test
    void listKeepsOnlyActiveGlobalAndSelectedLobbyAnnouncements() {
        selectTaizhou();
        AnnouncementEntity global = announcement(1L, null, true, NOW.minusSeconds(60), null, 1);
        AnnouncementEntity local = announcement(2L, TAIZHOU, true, null, NOW.plusSeconds(60), 1);
        AnnouncementEntity otherLobby = announcement(3L, 900038L, true, null, null, 1);
        AnnouncementEntity future = announcement(4L, null, true, NOW.plusSeconds(1), null, 1);
        AnnouncementEntity expired = announcement(5L, null, true, null, NOW, 1);
        AnnouncementEntity disabled = announcement(6L, null, false, null, null, 1);
        when(announcementRepository.findAllByOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(global, local, otherLobby, future, expired, disabled));

        AnnouncementListResponse response = service.list(USER_ID);

        assertThat(response.announcements())
                .extracting(AnnouncementSummary::announcementId)
                .containsExactly(1L, 2L);
    }

    @Test
    void listTreatsAReadAsCurrentOnlyWhenItsAnnouncementVersionMatches() {
        selectTaizhou();
        AnnouncementEntity current = announcement(1L, null, true, null, null, 4);
        AnnouncementEntity changed = announcement(2L, null, true, null, null, 5);
        when(announcementRepository.findAllByOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(current, changed));
        when(readRepository.findByIdUserIdAndIdAnnouncementIdIn(USER_ID, List.of(1L, 2L)))
                .thenReturn(List.of(
                        new AnnouncementReadEntity(USER_ID, 1L, 4, NOW.minusSeconds(10)),
                        new AnnouncementReadEntity(USER_ID, 2L, 4, NOW.minusSeconds(10))));

        AnnouncementListResponse response = service.list(USER_ID);

        assertThat(response.announcements())
                .extracting(AnnouncementSummary::read)
                .containsExactly(true, false);
    }

    @Test
    void listFallsBackToTheEnabledDefaultLobby() {
        when(selectionRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(lobbyRepository.findFirstByDefaultLobbyTrueAndEnabledTrueOrderBySortOrderAsc())
                .thenReturn(Optional.of(new RegionLobbyEntity(
                        TAIZHOU, "331000", "台州", 1, true, true)));
        when(announcementRepository.findAllByOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(announcement(1L, TAIZHOU, true, null, null, 1)));

        AnnouncementListResponse response = service.list(USER_ID);

        assertThat(response.lobbyId()).isEqualTo(TAIZHOU);
        assertThat(response.announcements()).hasSize(1);
    }

    private void selectTaizhou() {
        when(selectionRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserRegionSelectionEntity(USER_ID, TAIZHOU)));
        when(lobbyRepository.findByLobbyIdAndEnabledTrue(TAIZHOU))
                .thenReturn(Optional.of(new RegionLobbyEntity(
                        TAIZHOU, "331000", "台州", 1, true, true)));
    }

    private static AnnouncementEntity announcement(
            long id,
            Long lobbyId,
            boolean enabled,
            Instant startsAt,
            Instant endsAt,
            long version) {
        AnnouncementEntity entity = new AnnouncementEntity(
                "公告" + id,
                "副标题" + id,
                "正文" + id,
                null,
                lobbyId,
                (int) id,
                enabled,
                startsAt,
                endsAt,
                version);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
