package com.nanbei.entertainment.backend.avatar.api;

import com.nanbei.entertainment.backend.avatar.application.AvatarService;
import com.nanbei.entertainment.backend.avatar.application.StoredAvatar;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AvatarController {
    private static final CacheControl AVATAR_CACHE =
            CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PutMapping(
            path = "/profile/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AvatarUploadResponse upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        StoredAvatar stored =
                avatarService.save(
                        UUID.fromString(jwt.getSubject()),
                        file.getBytes(),
                        file.getContentType());
        return AvatarUploadResponse.from(stored);
    }

    @GetMapping("/avatars/{avatarKey}")
    ResponseEntity<byte[]> read(
            @PathVariable String avatarKey,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        StoredAvatar stored = avatarService.load(avatarKey);
        String etag = "\"" + stored.sha256() + "\"";
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl(AVATAR_CACHE);
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        headers.setContentType(MediaType.parseMediaType(stored.contentType()));
        headers.setContentLength(stored.bytes().length);
        return ResponseEntity.ok().headers(headers).body(stored.bytes());
    }

    public record AvatarUploadResponse(
            String avatarKey,
            String contentType,
            String sha256,
            int width,
            int height,
            Instant updatedAt) {
        static AvatarUploadResponse from(StoredAvatar stored) {
            return new AvatarUploadResponse(
                    stored.avatarKey(),
                    stored.contentType(),
                    stored.sha256(),
                    stored.width(),
                    stored.height(),
                    stored.updatedAt());
        }
    }
}
