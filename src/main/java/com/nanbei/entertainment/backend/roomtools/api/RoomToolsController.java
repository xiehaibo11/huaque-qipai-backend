package com.nanbei.entertainment.backend.roomtools.api;

import com.nanbei.entertainment.backend.roomtools.application.RoomMessageRequest;
import com.nanbei.entertainment.backend.roomtools.application.RoomMessageResponse;
import com.nanbei.entertainment.backend.roomtools.application.RoomToolReservationResponse;
import com.nanbei.entertainment.backend.roomtools.application.RoomToolType;
import com.nanbei.entertainment.backend.roomtools.application.RoomToolsService;
import com.nanbei.entertainment.backend.roomtools.application.RoomToolsStateResponse;
import com.nanbei.entertainment.backend.roomtools.application.RoomVoicePayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/game-sessions/{roomNumber}/tools")
public class RoomToolsController {
    private final RoomToolsService service;

    public RoomToolsController(RoomToolsService service) {
        this.service = service;
    }

    @GetMapping
    RoomToolsStateResponse state(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber) {
        return service.state(userId(jwt), roomNumber);
    }

    @PutMapping("/reservations/{type}")
    RoomToolReservationResponse reservation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber,
            @PathVariable RoomToolType type,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationRequest request) {
        return service.setReservation(
                userId(jwt), roomNumber, type, idempotencyKey, request.active());
    }

    @PostMapping("/messages")
    RoomMessageResponse message(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RoomMessageRequest request) {
        return service.sendMessage(userId(jwt), roomNumber, idempotencyKey, request);
    }

    @PostMapping(value = "/voice", consumes = "audio/mp4")
    RoomMessageResponse voice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Voice-Duration-Millis") @Min(400) @Max(30000) int durationMillis,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String mediaType,
            @RequestBody byte[] data) {
        return service.sendVoice(
                userId(jwt), roomNumber, idempotencyKey, mediaType, durationMillis, data);
    }

    @GetMapping("/voice/{messageId}")
    ResponseEntity<byte[]> voiceData(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber,
            @PathVariable UUID messageId) {
        RoomVoicePayload payload = service.voice(userId(jwt), roomNumber, messageId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(payload.mediaType()))
                .header("X-Voice-Duration-Millis", Integer.toString(payload.durationMillis()))
                .body(payload.data());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record ReservationRequest(boolean active) {}
}
