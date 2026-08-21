package com.nanbei.entertainment.backend.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "region_lobbies")
public class RegionLobbyEntity {
    @Id
    @Column(name = "lobby_id")
    private long lobbyId;

    @Column(name = "city_code", nullable = false, length = 24)
    private String cityCode;

    @Column(name = "area_name", nullable = false, length = 40)
    private String areaName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "default_lobby", nullable = false)
    private boolean defaultLobby;

    protected RegionLobbyEntity() {}

    public RegionLobbyEntity(
            long lobbyId,
            String cityCode,
            String areaName,
            int sortOrder,
            boolean enabled,
            boolean defaultLobby) {
        this.lobbyId = lobbyId;
        this.cityCode = cityCode;
        this.areaName = areaName;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.defaultLobby = defaultLobby;
    }

    public long getLobbyId() {
        return lobbyId;
    }

    public String getCityCode() {
        return cityCode;
    }

    public String getAreaName() {
        return areaName;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isDefaultLobby() {
        return defaultLobby;
    }
}
