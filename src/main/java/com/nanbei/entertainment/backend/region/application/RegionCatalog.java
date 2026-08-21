package com.nanbei.entertainment.backend.region.application;

import java.util.List;

public record RegionCatalog(long defaultLobbyId, List<City> cities) {
    public record City(
            String code,
            String name,
            int sortOrder,
            int mapX,
            int mapY,
            String secondaryMap,
            List<Lobby> lobbies) {}

    public record Lobby(long lobbyId, String areaName, int sortOrder) {}
}
