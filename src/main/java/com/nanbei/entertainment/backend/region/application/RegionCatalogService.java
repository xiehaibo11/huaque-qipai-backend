package com.nanbei.entertainment.backend.region.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.region.domain.RegionCityEntity;
import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import com.nanbei.entertainment.backend.region.domain.UserRegionSelectionEntity;
import com.nanbei.entertainment.backend.region.infrastructure.RegionCityRepository;
import com.nanbei.entertainment.backend.region.infrastructure.RegionLobbyRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegionCatalogService {
    private final RegionCityRepository cityRepository;
    private final RegionLobbyRepository lobbyRepository;
    private final UserRegionSelectionRepository selectionRepository;

    public RegionCatalogService(
            RegionCityRepository cityRepository,
            RegionLobbyRepository lobbyRepository,
            UserRegionSelectionRepository selectionRepository) {
        this.cityRepository = cityRepository;
        this.lobbyRepository = lobbyRepository;
        this.selectionRepository = selectionRepository;
    }

    @Transactional(readOnly = true)
    public RegionCatalog loadCatalog() {
        List<RegionLobbyEntity> lobbies =
                lobbyRepository.findByEnabledTrueOrderBySortOrderAsc();
        long defaultLobbyId =
                lobbies.stream()
                        .filter(RegionLobbyEntity::isDefaultLobby)
                        .findFirst()
                        .map(RegionLobbyEntity::getLobbyId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "区域目录未配置默认 lobby"));
        Map<String, List<RegionCatalog.Lobby>> lobbiesByCity =
                lobbies.stream()
                        .collect(
                                Collectors.groupingBy(
                                        RegionLobbyEntity::getCityCode,
                                        Collectors.mapping(
                                                lobby ->
                                                        new RegionCatalog.Lobby(
                                                                lobby.getLobbyId(),
                                                                lobby.getAreaName(),
                                                                lobby.getSortOrder()),
                                                Collectors.toList())));
        List<RegionCatalog.City> cities =
                cityRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                        .map(city -> toCity(city, lobbiesByCity))
                        .toList();
        return new RegionCatalog(defaultLobbyId, cities);
    }

    @Transactional
    public RegionSelection saveSelection(UUID userId, long lobbyId) {
        RegionLobbyEntity lobby =
                lobbyRepository
                        .findByLobbyIdAndEnabledTrue(lobbyId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.REGION_NOT_FOUND,
                                                "地区玩法不存在或暂未开放"));
        UserRegionSelectionEntity selection =
                selectionRepository
                        .findById(userId)
                        .map(
                                existing -> {
                                    existing.select(lobby.getLobbyId());
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        new UserRegionSelectionEntity(
                                                userId, lobby.getLobbyId()));
        UserRegionSelectionEntity saved = selectionRepository.save(selection);
        return new RegionSelection(
                saved.getUserId(), saved.getLobbyId(), saved.getUpdatedAt());
    }

    private static RegionCatalog.City toCity(
            RegionCityEntity city,
            Map<String, List<RegionCatalog.Lobby>> lobbiesByCity) {
        return new RegionCatalog.City(
                city.getCode(),
                city.getName(),
                city.getSortOrder(),
                city.getMapX(),
                city.getMapY(),
                city.getSecondaryMap(),
                lobbiesByCity.getOrDefault(city.getCode(), List.of()));
    }
}
