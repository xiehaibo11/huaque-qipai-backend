package com.nanbei.entertainment.backend.friend.api;

import com.nanbei.entertainment.backend.friend.application.FriendApplicationView.FriendApplicationList;
import com.nanbei.entertainment.backend.friend.application.FriendInviteAllResult;
import com.nanbei.entertainment.backend.friend.application.FriendNotificationService;
import com.nanbei.entertainment.backend.friend.application.FriendNotificationView.FriendNotificationList;
import com.nanbei.entertainment.backend.friend.application.FriendPage;
import com.nanbei.entertainment.backend.friend.application.FriendSearchResult;
import com.nanbei.entertainment.backend.friend.application.FriendSearchService;
import com.nanbei.entertainment.backend.friend.application.FriendService;
import com.nanbei.entertainment.backend.friend.domain.FriendNotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {
    private final FriendService friendService;
    private final FriendSearchService searchService;
    private final FriendNotificationService notificationService;

    public FriendController(
            FriendService friendService,
            FriendSearchService searchService,
            FriendNotificationService notificationService) {
        this.friendService = friendService;
        this.searchService = searchService;
        this.notificationService = notificationService;
    }

    @GetMapping
    FriendPage list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return friendService.listFriends(userId(jwt), page, size);
    }

    @GetMapping("/search")
    FriendSearchResult search(
            @AuthenticationPrincipal Jwt jwt, @RequestParam String query) {
        return searchService.search(userId(jwt), query);
    }

    @PostMapping("/applications")
    ResponseEntity<Void> apply(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FriendApplyRequest request) {
        friendService.apply(userId(jwt), request.publicPlayerId());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/applications")
    FriendApplicationList applications(@AuthenticationPrincipal Jwt jwt) {
        return friendService.incomingApplications(userId(jwt));
    }

    @PostMapping("/applications/{id}/accept")
    void accept(
            @AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        friendService.accept(userId(jwt), id);
    }

    @PostMapping("/applications/by-player/{publicPlayerId}/accept")
    void acceptByPlayer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long publicPlayerId) {
        friendService.acceptByPublicPlayerId(userId(jwt), publicPlayerId);
    }

    @PostMapping("/applications/{id}/reject")
    void reject(
            @AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        friendService.reject(userId(jwt), id);
    }

    @PostMapping("/applications/by-player/{publicPlayerId}/reject")
    void rejectByPlayer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long publicPlayerId) {
        friendService.rejectByPublicPlayerId(userId(jwt), publicPlayerId);
    }

    @DeleteMapping("/{publicPlayerId}")
    void remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long publicPlayerId) {
        friendService.removeFriend(userId(jwt), publicPlayerId);
    }

    @PutMapping("/{publicPlayerId}/shield")
    void shield(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long publicPlayerId,
            @Valid @RequestBody FriendShieldRequest request) {
        friendService.setShielded(
                userId(jwt), publicPlayerId, request.shielded());
    }

    @PostMapping("/{publicPlayerId}/invite")
    ResponseEntity<Void> invite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long publicPlayerId,
            @Valid @RequestBody FriendInviteRequest request) {
        notificationService.invite(
                userId(jwt), publicPlayerId, request.type());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/invite-all")
    ResponseEntity<FriendInviteAllResult> inviteAll(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted()
                .body(notificationService.inviteAll(userId(jwt)));
    }

    @GetMapping("/notifications")
    FriendNotificationList notifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unread) {
        return notificationService.notifications(userId(jwt), unread);
    }

    @PostMapping("/notifications/read")
    ResponseEntity<Void> readAll(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllNotificationsRead(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record FriendApplyRequest(@NotNull Long publicPlayerId) {}

    public record FriendShieldRequest(@NotNull Boolean shielded) {}

    public record FriendInviteRequest(@NotNull FriendNotificationType type) {}
}
