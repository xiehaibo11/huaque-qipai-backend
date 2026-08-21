package com.nanbei.entertainment.backend.personalcenter.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.personalcenter.domain.FeedbackCategory;
import com.nanbei.entertainment.backend.personalcenter.domain.PrivacySettingsEntity;
import com.nanbei.entertainment.backend.personalcenter.domain.UserFeedbackEntity;
import com.nanbei.entertainment.backend.personalcenter.infrastructure.PrivacySettingsRepository;
import com.nanbei.entertainment.backend.personalcenter.infrastructure.UserFeedbackRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalCenterFunctionService {
    private final PrivacySettingsRepository privacyRepository;
    private final UserFeedbackRepository feedbackRepository;

    public PersonalCenterFunctionService(
            PrivacySettingsRepository privacyRepository,
            UserFeedbackRepository feedbackRepository) {
        this.privacyRepository = privacyRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public PersonalCenterPrivacySettings loadPrivacy(UUID userId) {
        return privacyRepository.findById(userId)
                .map(PersonalCenterFunctionService::toSettings)
                .orElseGet(PersonalCenterPrivacySettings::defaults);
    }

    @Transactional
    public PersonalCenterPrivacySettings updatePrivacy(
            UUID userId, PersonalCenterPrivacySettings requested) {
        PrivacySettingsEntity entity =
                privacyRepository.findById(userId)
                        .orElseGet(() -> new PrivacySettingsEntity(userId));
        entity.update(
                requested.allowFriendRequests(),
                requested.showGameRecord(),
                requested.showOnlineStatus(),
                requested.chatNotifications(),
                requested.personalizedRecommendations());
        return toSettings(privacyRepository.save(entity));
    }

    @Transactional
    public PersonalCenterFeedbackItem submitFeedback(
            UUID userId,
            FeedbackCategory category,
            String content) {
        String normalized = content == null ? "" : content.trim();
        if (category == null
                || normalized.isEmpty()
                || normalized.length() > 500) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "反馈内容须为1至500个字符");
        }
        UserFeedbackEntity saved =
                feedbackRepository.save(
                        new UserFeedbackEntity(
                                userId, category, normalized));
        return toItem(saved);
    }

    @Transactional(readOnly = true)
    public List<PersonalCenterFeedbackItem> feedbackHistory(
            UUID userId) {
        return feedbackRepository
                .findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PersonalCenterFunctionService::toItem)
                .toList();
    }

    private static PersonalCenterPrivacySettings toSettings(
            PrivacySettingsEntity entity) {
        return new PersonalCenterPrivacySettings(
                entity.isAllowFriendRequests(),
                entity.isShowGameRecord(),
                entity.isShowOnlineStatus(),
                entity.isChatNotifications(),
                entity.isPersonalizedRecommendations());
    }

    private static PersonalCenterFeedbackItem toItem(
            UserFeedbackEntity entity) {
        return new PersonalCenterFeedbackItem(
                entity.getId(),
                entity.getCategory(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreatedAt());
    }
}
