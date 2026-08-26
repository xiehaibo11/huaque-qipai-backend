package com.nanbei.entertainment.backend.announcement.application;

import com.nanbei.entertainment.backend.announcement.domain.AnnouncementEntity;
import com.nanbei.entertainment.backend.announcement.domain.AnnouncementReadEntity;
import com.nanbei.entertainment.backend.announcement.domain.AnnouncementReadId;
import com.nanbei.entertainment.backend.announcement.infrastructure.AnnouncementReadRepository;
import com.nanbei.entertainment.backend.announcement.infrastructure.AnnouncementRepository;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import com.nanbei.entertainment.backend.region.infrastructure.RegionLobbyRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementService {
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository readRepository;
    private final UserRegionSelectionRepository selectionRepository;
    private final RegionLobbyRepository lobbyRepository;
    private final Clock clock;

    @Autowired
    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            AnnouncementReadRepository readRepository,
            UserRegionSelectionRepository selectionRepository,
            RegionLobbyRepository lobbyRepository) {
        this(
                announcementRepository,
                readRepository,
                selectionRepository,
                lobbyRepository,
                Clock.systemUTC());
    }

    AnnouncementService(
            AnnouncementRepository announcementRepository,
            AnnouncementReadRepository readRepository,
            UserRegionSelectionRepository selectionRepository,
            RegionLobbyRepository lobbyRepository,
            Clock clock) {
        this.announcementRepository = announcementRepository;
        this.readRepository = readRepository;
        this.selectionRepository = selectionRepository;
        this.lobbyRepository = lobbyRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnnouncementListResponse list(UUID userId) {
        long lobbyId = resolveLobby(userId).getLobbyId();
        Instant now = clock.instant();
        List<AnnouncementEntity> visible = announcementRepository
                .findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(announcement -> announcement.isVisibleTo(lobbyId, now))
                .toList();
        Map<Long, AnnouncementReadEntity> reads = reads(userId, visible);
        List<AnnouncementSummary> summaries = visible.stream()
                .map(announcement -> summary(
                        announcement,
                        readCurrent(reads.get(announcement.getId()), announcement)))
                .toList();
        return new AnnouncementListResponse(lobbyId, summaries);
    }

    @Transactional
    public AnnouncementDetailResponse detail(UUID userId, long announcementId) {
        long lobbyId = resolveLobby(userId).getLobbyId();
        Instant now = clock.instant();
        AnnouncementEntity announcement = announcementRepository
                .findById(announcementId)
                .filter(candidate -> candidate.isVisibleTo(lobbyId, now))
                .orElseThrow(() -> new ApiException(
                        ErrorCode.ANNOUNCEMENT_NOT_FOUND, "公告不存在"));
        AnnouncementReadId readId = new AnnouncementReadId(userId, announcementId);
        AnnouncementReadEntity read = readRepository
                .findById(readId)
                .orElseGet(() -> new AnnouncementReadEntity(
                        userId, announcementId, announcement.getVersion(), now));
        read.mark(announcement.getVersion(), now);
        readRepository.save(read);
        return new AnnouncementDetailResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getSubtitle(),
                announcement.getBodyText(),
                announcement.getPageUrl(),
                announcement.getLobbyId(),
                announcement.getSortOrder(),
                announcement.getStartsAt(),
                announcement.getEndsAt(),
                announcement.getVersion(),
                true,
                read.getReadAt());
    }

    @Transactional
    public AnnouncementReadResponse markRead(UUID userId, long announcementId) {
        AnnouncementDetailResponse detail = detail(userId, announcementId);
        return new AnnouncementReadResponse(
                detail.announcementId(), detail.version(), true, detail.readAt());
    }

    private RegionLobbyEntity resolveLobby(UUID userId) {
        return selectionRepository
                .findById(userId)
                .flatMap(selection ->
                        lobbyRepository.findByLobbyIdAndEnabledTrue(selection.getLobbyId()))
                .or(() -> lobbyRepository
                        .findFirstByDefaultLobbyTrueAndEnabledTrueOrderBySortOrderAsc())
                .orElseThrow(() -> new ApiException(ErrorCode.REGION_NOT_FOUND, "当前大厅不可用"));
    }

    private Map<Long, AnnouncementReadEntity> reads(
            UUID userId, Collection<AnnouncementEntity> announcements) {
        List<Long> ids = announcements.stream().map(AnnouncementEntity::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return readRepository.findByIdUserIdAndIdAnnouncementIdIn(userId, ids).stream()
                .collect(Collectors.toMap(
                        AnnouncementReadEntity::getAnnouncementId, Function.identity()));
    }

    private static boolean readCurrent(
            AnnouncementReadEntity read, AnnouncementEntity announcement) {
        return read != null && read.getAnnouncementVersion() == announcement.getVersion();
    }

    private static AnnouncementSummary summary(
            AnnouncementEntity announcement, boolean read) {
        return new AnnouncementSummary(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getSubtitle(),
                announcement.getBodyText(),
                announcement.getPageUrl(),
                announcement.getLobbyId(),
                announcement.getSortOrder(),
                announcement.getStartsAt(),
                announcement.getEndsAt(),
                announcement.getVersion(),
                read);
    }
}
