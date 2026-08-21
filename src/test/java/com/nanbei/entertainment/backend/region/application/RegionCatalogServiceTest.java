package com.nanbei.entertainment.backend.region.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.region.domain.RegionCityEntity;
import com.nanbei.entertainment.backend.region.domain.RegionLobbyEntity;
import com.nanbei.entertainment.backend.region.domain.UserRegionSelectionEntity;
import com.nanbei.entertainment.backend.region.infrastructure.RegionCityRepository;
import com.nanbei.entertainment.backend.region.infrastructure.RegionLobbyRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegionCatalogServiceTest {
    @Mock RegionCityRepository cityRepository;
    @Mock RegionLobbyRepository lobbyRepository;
    @Mock UserRegionSelectionRepository selectionRepository;

    RegionCatalogService service;

    @BeforeEach
    void setUp() {
        service =
                new RegionCatalogService(
                        cityRepository, lobbyRepository, selectionRepository);
    }

    @Test
    void returnsEnabledCitiesWithReverseRecoveredLobbyOrdering() {
        RegionCityEntity taizhou =
                new RegionCityEntity("taizhou", "台州", 9, 949, 560, true);
        RegionLobbyEntity taizhouLobby =
                new RegionLobbyEntity(
                        900023L, "taizhou", "台州", 5, true, true);
        when(cityRepository.findByEnabledTrueOrderBySortOrderAsc())
                .thenReturn(List.of(taizhou));
        when(lobbyRepository.findByEnabledTrueOrderBySortOrderAsc())
                .thenReturn(List.of(taizhouLobby));

        RegionCatalog result = service.loadCatalog();

        assertThat(result.defaultLobbyId()).isEqualTo(900023L);
        assertThat(result.cities()).hasSize(1);
        assertThat(result.cities().getFirst().code()).isEqualTo("taizhou");
        assertThat(result.cities().getFirst().lobbies())
                .extracting(RegionCatalog.Lobby::lobbyId)
                .containsExactly(900023L);
    }

    @Test
    void persistsAValidatedEnabledLobbyForTheAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        RegionLobbyEntity taizhouLobby =
                new RegionLobbyEntity(
                        900023L, "taizhou", "台州", 5, true, true);
        when(lobbyRepository.findByLobbyIdAndEnabledTrue(900023L))
                .thenReturn(Optional.of(taizhouLobby));
        when(selectionRepository.save(any(UserRegionSelectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegionSelection result = service.saveSelection(userId, 900023L);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.lobbyId()).isEqualTo(900023L);
        verify(selectionRepository).save(any(UserRegionSelectionEntity.class));
    }
}
