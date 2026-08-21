package com.nanbei.entertainment.backend.personalcenter.application;

import com.nanbei.entertainment.backend.personalcenter.domain.FeedbackCategory;
import com.nanbei.entertainment.backend.personalcenter.domain.FeedbackStatus;
import java.time.Instant;
import java.util.UUID;

public record PersonalCenterFeedbackItem(
        UUID id,
        FeedbackCategory category,
        String content,
        FeedbackStatus status,
        Instant createdAt) {}
