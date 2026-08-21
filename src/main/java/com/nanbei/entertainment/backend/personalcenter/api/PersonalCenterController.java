package com.nanbei.entertainment.backend.personalcenter.api;

import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterService;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterSnapshot;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterFeedbackItem;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterFunctionService;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterPrivacySettings;
import com.nanbei.entertainment.backend.personalcenter.domain.FeedbackCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/personal-center")
public class PersonalCenterController {
    private final PersonalCenterService personalCenterService;
    private final PersonalCenterFunctionService functionService;

    public PersonalCenterController(
            PersonalCenterService personalCenterService,
            PersonalCenterFunctionService functionService) {
        this.personalCenterService = personalCenterService;
        this.functionService = functionService;
    }

    @GetMapping
    PersonalCenterSnapshot personalCenter(
            @AuthenticationPrincipal Jwt jwt) {
        return personalCenterService.load(
                UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/privacy")
    PersonalCenterPrivacySettings privacy(
            @AuthenticationPrincipal Jwt jwt) {
        return functionService.loadPrivacy(userId(jwt));
    }

    @PutMapping("/privacy")
    PersonalCenterPrivacySettings updatePrivacy(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PersonalCenterPrivacySettings request) {
        return functionService.updatePrivacy(userId(jwt), request);
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    PersonalCenterFeedbackItem submitFeedback(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FeedbackRequest request) {
        return functionService.submitFeedback(
                userId(jwt), request.category(), request.content());
    }

    @GetMapping("/feedback")
    List<PersonalCenterFeedbackItem> feedbackHistory(
            @AuthenticationPrincipal Jwt jwt) {
        return functionService.feedbackHistory(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record FeedbackRequest(
            @NotNull FeedbackCategory category,
            @NotBlank @Size(max = 500) String content) {}
}
