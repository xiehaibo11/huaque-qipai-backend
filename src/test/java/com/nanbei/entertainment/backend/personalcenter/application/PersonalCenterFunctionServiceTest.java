package com.nanbei.entertainment.backend.personalcenter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.personalcenter.domain.FeedbackCategory;
import com.nanbei.entertainment.backend.personalcenter.domain.PrivacySettingsEntity;
import com.nanbei.entertainment.backend.personalcenter.domain.UserFeedbackEntity;
import com.nanbei.entertainment.backend.personalcenter.infrastructure.PrivacySettingsRepository;
import com.nanbei.entertainment.backend.personalcenter.infrastructure.UserFeedbackRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalCenterFunctionServiceTest {
    @Mock PrivacySettingsRepository privacyRepository;
    @Mock UserFeedbackRepository feedbackRepository;

    PersonalCenterFunctionService service;

    @BeforeEach
    void setUp() {
        service =
                new PersonalCenterFunctionService(
                        privacyRepository, feedbackRepository);
    }

    @Test
    void returnsDocumentedPrivacyDefaultsWithoutWritingOnRead() {
        UUID userId = UUID.randomUUID();
        when(privacyRepository.findById(userId))
                .thenReturn(Optional.empty());

        PersonalCenterPrivacySettings result =
                service.loadPrivacy(userId);

        assertThat(result.allowFriendRequests()).isTrue();
        assertThat(result.showGameRecord()).isTrue();
        assertThat(result.showOnlineStatus()).isTrue();
        assertThat(result.chatNotifications()).isTrue();
        assertThat(result.personalizedRecommendations()).isFalse();
        assertThat(result.clipboardAccessEnabled()).isTrue();
    }

    @Test
    void savesTheCompletePrivacySelectionForTheAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        when(privacyRepository.findById(userId))
                .thenReturn(Optional.empty());
        when(privacyRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PersonalCenterPrivacySettings requested =
                new PersonalCenterPrivacySettings(
                        false, true, false, true, true, false);

        PersonalCenterPrivacySettings result =
                service.updatePrivacy(userId, requested);

        ArgumentCaptor<PrivacySettingsEntity> captor =
                ArgumentCaptor.forClass(PrivacySettingsEntity.class);
        verify(privacyRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(result).isEqualTo(requested);
    }

    @Test
    void trimsAndStoresFeedbackAndReturnsNewestFirstHistory() {
        UUID userId = UUID.randomUUID();
        when(feedbackRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonalCenterFeedbackItem submitted =
                service.submitFeedback(
                        userId,
                        FeedbackCategory.FEEDBACK,
                        "  大厅按钮偶尔没有声音  ");

        assertThat(submitted.content())
                .isEqualTo("大厅按钮偶尔没有声音");
        assertThat(submitted.category())
                .isEqualTo(FeedbackCategory.FEEDBACK);
        verify(feedbackRepository).save(any(UserFeedbackEntity.class));

        UserFeedbackEntity report =
                new UserFeedbackEntity(
                        userId, FeedbackCategory.REPORT, "举报不良聊天");
        when(feedbackRepository
                        .findTop20ByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(report));

        assertThat(service.feedbackHistory(userId))
                .extracting(PersonalCenterFeedbackItem::content)
                .containsExactly("举报不良聊天");
    }

    @Test
    void rejectsBlankOrOversizedFeedbackBeforePersistence() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.submitFeedback(
                                        userId,
                                        FeedbackCategory.FEEDBACK,
                                        "   "))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                service.submitFeedback(
                                        userId,
                                        FeedbackCategory.REPORT,
                                        "x".repeat(501)))
                .isInstanceOf(ApiException.class);
    }
}
