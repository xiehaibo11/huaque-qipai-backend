package com.nanbei.entertainment.backend.gameplay.application;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record GameplayEventView(
        UUID sessionId,
        long revision,
        int eventOrder,
        String type,
        JsonNode payload) {}
