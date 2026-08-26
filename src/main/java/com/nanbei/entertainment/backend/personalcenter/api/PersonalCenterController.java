package com.nanbei.entertainment.backend.personalcenter.api;

import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterAccountService;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterService;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterSnapshot;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterFeedbackItem;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterFunctionService;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterPrivacySettings;
import com.nanbei.entertainment.backend.personalcenter.domain.FeedbackCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final PersonalCenterAccountService accountService;

    public PersonalCenterController(
            PersonalCenterService personalCenterService,
            PersonalCenterFunctionService functionService,
            PersonalCenterAccountService accountService) {
        this.personalCenterService = personalCenterService;
        this.functionService = functionService;
        this.accountService = accountService;
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

    @PostMapping("/phone/code")
    PersonalCenterAccountService.PhoneCodeResult requestPhoneCode(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PhoneCodeRequest request) {
        return accountService.requestPhoneCode(
                userId(jwt), request.phoneNumber());
    }

    @PutMapping("/phone")
    PersonalCenterAccountService.PhoneBindingResult bindPhone(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PhoneBindingRequest request) {
        return accountService.bindPhone(
                userId(jwt), request.phoneNumber(), request.code());
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivateAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountDeletionRequest request) {
        accountService.deactivateAccount(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record FeedbackRequest(
            @NotNull FeedbackCategory category,
            @NotBlank @Size(max = 500) String content) {}

    public record PhoneCodeRequest(
            @NotBlank @Size(max = 32) String phoneNumber) {}

    public record PhoneBindingRequest(
            @NotBlank @Size(max = 32) String phoneNumber,
            @NotBlank @Size(min = 4, max = 8) String code) {}

    public record AccountDeletionRequest(
            @NotBlank @Pattern(regexp = "注销账号") String confirmation) {}
}
