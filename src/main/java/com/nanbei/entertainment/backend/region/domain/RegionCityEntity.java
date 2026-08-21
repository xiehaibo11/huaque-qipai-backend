package com.nanbei.entertainment.backend.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "region_cities")
public class RegionCityEntity {
    @Id
    @Column(length = 24)
    private String code;

    @Column(nullable = false, length = 24)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "map_x", nullable = false)
    private int mapX;

    @Column(name = "map_y", nullable = false)
    private int mapY;

    @Column(name = "secondary_map", length = 80)
    private String secondaryMap;

    @Column(nullable = false)
    private boolean enabled;

    protected RegionCityEntity() {}

    public RegionCityEntity(
            String code,
            String name,
            int sortOrder,
            int mapX,
            int mapY,
            boolean enabled) {
        this(code, name, sortOrder, mapX, mapY, null, enabled);
    }

    public RegionCityEntity(
            String code,
            String name,
            int sortOrder,
            int mapX,
            int mapY,
            String secondaryMap,
            boolean enabled) {
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
        this.mapX = mapX;
        this.mapY = mapY;
        this.secondaryMap = secondaryMap;
        this.enabled = enabled;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getMapX() {
        return mapX;
    }

    public int getMapY() {
        return mapY;
    }

    public String getSecondaryMap() {
        return secondaryMap;
    }
}
