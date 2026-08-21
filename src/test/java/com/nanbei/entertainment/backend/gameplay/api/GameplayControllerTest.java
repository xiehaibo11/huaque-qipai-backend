package com.nanbei.entertainment.backend.gameplay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandRequest;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandResponse;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandService;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandType;
import com.nanbei.entertainment.backend.gameplay.application.GameplayEventService;
import com.nanbei.entertainment.backend.gameplay.application.GameplayEventView;
import com.nanbei.entertainment.backend.gameplay.application.GameplaySessionService;
import com.nanbei.entertainment.backend.gameplay.application.GameplaySnapshot;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameplayControllerTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Mock GameplaySessionService sessionService;
    @Mock GameplayCommandService commandService;
    @Mock GameplayEventService eventService;

    @Test
    void opensSessionForJwtSubjectAndReturnsCreatedLocation() {
        GameplaySnapshot expected = snapshot();
        when(sessionService.open(USER_ID, "123456")).thenReturn(expected);

        ResponseEntity<GameplaySnapshot> response = controller().open(jwt(), "123456");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo("/api/v1/game-sessions/123456");
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void readsSnapshotForJwtSubject() {
        GameplaySnapshot expected = snapshot();
        when(sessionService.get(USER_ID, "123456")).thenReturn(expected);

        assertThat(controller().get(jwt(), "123456")).isSameAs(expected);
    }

    @Test
    void submitsCommandUsingHeaderAndJwtSubject() {
        GameplayCommandRequest request =
                new GameplayCommandRequest(GameplayCommandType.READY, 0L);
        GameplayCommandResponse expected =
                new GameplayCommandResponse(1L, "SEAT_READY_CHANGED", 1, true, false);
        when(commandService.submit(USER_ID, "123456", "command-key", request))
                .thenReturn(expected);

        GameplayCommandResponse actual =
                controller().command(jwt(), "123456", "command-key", request);

        assertThat(actual).isSameAs(expected);
        verify(commandService).submit(USER_ID, "123456", "command-key", request);
    }

    @Test
    void recoversEventsForJwtSubject() {
        GameplayEventView expected =
                new GameplayEventView(
                        UUID.randomUUID(),
                        1L,
                        1,
                        "SEAT_READY_CHANGED",
                        new ObjectMapper().createObjectNode());
        when(eventService.after(USER_ID, "123456", 0L)).thenReturn(List.of(expected));

        assertThat(controller().events(jwt(), "123456", 0L)).containsExactly(expected);
    }

    private GameplayController controller() {
        return new GameplayController(sessionService, commandService, eventService);
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
                List.of(),
                Instant.parse("2026-08-10T12:00:00Z"));
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
    }
}
