package com.nanbei.entertainment.backend.gameplay.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nanbei.entertainment.backend.common.config.SecurityProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.common.error.GlobalExceptionHandler;
import com.nanbei.entertainment.backend.common.security.SecurityConfiguration;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandRequest;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandResponse;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandService;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandType;
import com.nanbei.entertainment.backend.gameplay.application.GameplayEventService;
import com.nanbei.entertainment.backend.gameplay.application.GameplayEventView;
import com.nanbei.entertainment.backend.gameplay.application.GameplaySeatSnapshot;
import com.nanbei.entertainment.backend.gameplay.application.GameplaySessionService;
import com.nanbei.entertainment.backend.gameplay.application.GameplaySnapshot;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(
        controllers = GameplayController.class,
        properties = "nanbei.security.jwt-secret=01234567890123456789012345678901")
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
@EnableConfigurationProperties(SecurityProperties.class)
class GameplayControllerWebMvcTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String AUTHORIZATION = "Bearer gameplay-test-token";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean GameplaySessionService sessionService;
    @MockitoBean GameplayCommandService commandService;
    @MockitoBean GameplayEventService eventService;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void authenticateTestToken() {
        when(jwtDecoder.decode("gameplay-test-token")).thenReturn(jwt());
    }

    @Test
    void rejectsSessionAccessWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/game-sessions/123456"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void opensSessionWithJwtIdentityAndCreatedLocation() throws Exception {
        when(sessionService.open(USER_ID, "123456")).thenReturn(snapshot());

        mockMvc.perform(
                        post("/api/v1/game-sessions/123456")
                                .header("Authorization", AUTHORIZATION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/game-sessions/123456"))
                .andExpect(jsonPath("$.gameId").value(30109))
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.chairCount").value(2))
                .andExpect(jsonPath("$.maxPlayCount").value(8))
                .andExpect(jsonPath("$.gameRuleDisplay").value("不平搓/不封顶"))
                .andExpect(jsonPath("$.autoReady").value(false))
                .andExpect(jsonPath("$.mySeat").value(1))
                .andExpect(jsonPath("$.seats[0].publicPlayerId").value(1084375590L))
                .andExpect(jsonPath("$.seats[0].displayName").value("牌桌玩家"))
                .andExpect(jsonPath("$.seats[0].avatarKey").value("avatar_default"))
                .andExpect(jsonPath("$.seats[0].score").value(1000))
                .andExpect(jsonPath("$.seats[0].host").value(true));
    }

    @Test
    void mapsOutsiderToForbiddenProblem() throws Exception {
        when(sessionService.get(USER_ID, "123456"))
                .thenThrow(new ApiException(ErrorCode.GAMEPLAY_FORBIDDEN, "无权查看该牌局"));

        mockMvc.perform(
                        get("/api/v1/game-sessions/123456")
                                .header("Authorization", AUTHORIZATION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAMEPLAY_FORBIDDEN"));
    }

    @Test
    void requiresIdempotencyHeaderAndSubmitsTypedCommand() throws Exception {
        GameplayCommandRequest request =
                new GameplayCommandRequest(GameplayCommandType.READY, 0L);
        when(commandService.submit(USER_ID, "123456", "ready-1", request))
                .thenReturn(
                        new GameplayCommandResponse(
                                1L,
                                "SEAT_READY_CHANGED",
                                1,
                                true,
                                false,
                                List.of(
                                        new GameplayEventView(
                                                UUID.randomUUID(),
                                                1L,
                                                1,
                                                "SEAT_READY_CHANGED",
                                                objectMapper.createObjectNode()))));
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(
                        post("/api/v1/game-sessions/123456/commands")
                                .header("Authorization", AUTHORIZATION)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        post("/api/v1/game-sessions/123456/commands")
                                .header("Authorization", AUTHORIZATION)
                                .header("Idempotency-Key", "ready-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.events[0].eventOrder").value(1))
                .andExpect(jsonPath("$.events[0].type").value("SEAT_READY_CHANGED"));
        verify(commandService).submit(USER_ID, "123456", "ready-1", request);
    }

    @Test
    void returnsEventsInServiceOrderAfterRequestedRevision() throws Exception {
        when(eventService.after(USER_ID, "123456", 7L))
                .thenReturn(
                        List.of(
                                event(8L, "SEAT_READY_CHANGED"),
                                event(9L, "SEAT_READY_CHANGED")));

        mockMvc.perform(
                        get("/api/v1/game-sessions/123456/events")
                                .header("Authorization", AUTHORIZATION)
                                .param("afterRevision", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revision").value(8))
                .andExpect(jsonPath("$[1].revision").value(9));
        verify(eventService).after(USER_ID, "123456", 7L);
    }

    private static GameplaySnapshot snapshot() {
        return new GameplaySnapshot(
                UUID.randomUUID(),
                "123456",
                30109L,
                GamePhase.WAITING,
                0,
                0L,
                2,
                8,
                "不平搓/不封顶",
                false,
                1,
                List.of(
                        new GameplaySeatSnapshot(
                                1,
                                USER_ID,
                                1084375590L,
                                "牌桌玩家",
                                "avatar_default",
                                1000L,
                                true,
                                false,
                                true)),
                Instant.parse("2026-08-10T12:00:00Z"));
    }

    private static GameplayEventView event(long revision, String type) {
        return new GameplayEventView(
                UUID.randomUUID(),
                revision,
                1,
                type,
                new ObjectMapper().createObjectNode());
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("gameplay-test-token")
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .build();
    }
}
