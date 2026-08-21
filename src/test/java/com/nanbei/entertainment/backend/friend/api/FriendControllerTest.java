package com.nanbei.entertainment.backend.friend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.friend.application.FriendApplicationView.FriendApplicationList;
import com.nanbei.entertainment.backend.friend.application.FriendInviteAllResult;
import com.nanbei.entertainment.backend.friend.application.FriendNotificationService;
import com.nanbei.entertainment.backend.friend.application.FriendPage;
import com.nanbei.entertainment.backend.friend.application.FriendSearchResult;
import com.nanbei.entertainment.backend.friend.application.FriendSearchService;
import com.nanbei.entertainment.backend.friend.application.FriendService;
import com.nanbei.entertainment.backend.friend.domain.FriendNotificationType;
import com.nanbei.entertainment.backend.friend.domain.FriendRelation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class FriendControllerTest {
    @Mock FriendService friendService;
    @Mock FriendSearchService searchService;
    @Mock FriendNotificationService notificationService;

    @Test
    void listsFriendsForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        FriendPage expected = new FriendPage(0, 20, false, List.of());
        when(friendService.listFriends(userId, 0, 20))
                .thenReturn(expected);

        FriendPage actual =
                controller().list(jwt(userId), 0, 20);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void searchesForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        FriendSearchResult expected =
                new FriendSearchResult(
                        1084375590L,
                        "牌友甲",
                        "avatar_default",
                        FriendRelation.NONE,
                        null);
        when(searchService.search(userId, "1084375590"))
                .thenReturn(expected);

        FriendSearchResult actual =
                controller().search(jwt(userId), "1084375590");

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void appliesWithAcceptedStatus() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<Void> response =
                controller()
                        .apply(
                                jwt(userId),
                                new FriendController.FriendApplyRequest(
                                        1084375590L));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        verify(friendService).apply(userId, 1084375590L);
    }

    @Test
    void listsIncomingApplicationsForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        FriendApplicationList expected =
                new FriendApplicationList(0, List.of());
        when(friendService.incomingApplications(userId))
                .thenReturn(expected);

        assertThat(controller().applications(jwt(userId)))
                .isSameAs(expected);
    }

    @Test
    void acceptsApplicationForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        controller().accept(jwt(userId), applicationId);

        verify(friendService).accept(userId, applicationId);
    }

    @Test
    void acceptsApplicationByPublicPlayerIdLikeOriginalNumid() {
        UUID userId = UUID.randomUUID();

        controller().acceptByPlayer(jwt(userId), 1084375590L);

        verify(friendService).acceptByPublicPlayerId(
                userId, 1084375590L);
    }

    @Test
    void rejectsApplicationByPublicPlayerIdLikeOriginalNumid() {
        UUID userId = UUID.randomUUID();

        controller().rejectByPlayer(jwt(userId), 1084375590L);

        verify(friendService).rejectByPublicPlayerId(
                userId, 1084375590L);
    }

    @Test
    void invitesWithAcceptedStatus() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<Void> response =
                controller()
                        .invite(
                                jwt(userId),
                                1084375590L,
                                new FriendController.FriendInviteRequest(
                                        FriendNotificationType.RESERVE));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        verify(notificationService)
                .invite(
                        userId,
                        1084375590L,
                        FriendNotificationType.RESERVE);
    }

    @Test
    void invitesAllOnlineFriendsWithAcceptedStatus() {
        UUID userId = UUID.randomUUID();
        FriendInviteAllResult expected = new FriendInviteAllResult(2, 1);
        when(notificationService.inviteAll(userId)).thenReturn(expected);

        ResponseEntity<FriendInviteAllResult> response =
                controller().inviteAll(jwt(userId));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void marksNotificationsReadWithNoContentStatus() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<Void> response = controller().readAll(jwt(userId));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(notificationService).markAllNotificationsRead(userId);
    }

    private FriendController controller() {
        return new FriendController(
                friendService, searchService, notificationService);
    }

    private static Jwt jwt(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
    }
}
