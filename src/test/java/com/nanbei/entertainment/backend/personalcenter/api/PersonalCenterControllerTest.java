package com.nanbei.entertainment.backend.personalcenter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterService;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterSnapshot;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterAccountService;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterFunctionService;
import com.nanbei.entertainment.backend.personalcenter.application.PersonalCenterPrivacySettings;
import com.nanbei.entertainment.backend.personalcenter.domain.FeedbackCategory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class PersonalCenterControllerTest {
    @Mock PersonalCenterService service;
    @Mock PersonalCenterFunctionService functionService;
    @Mock PersonalCenterAccountService accountService;

    @Test
    void loadsThePersonalCenterForTheAuthenticatedJwtSubject() {
        UUID userId = UUID.randomUUID();
        PersonalCenterSnapshot expected =
                new PersonalCenterSnapshot(
                        new PersonalCenterSnapshot.Player(
                                userId,
                                1084375590L,
                                "WhimSeeker",
                                "avatar-user",
                                1),
                        new PersonalCenterSnapshot.Wallet(9L, 2L, 1835L, 0L),
                        new PersonalCenterSnapshot.Account(
                                true,
                                "158****6092",
                                List.of("PHONE")),
                        new PersonalCenterSnapshot.Region(900021L, "台州"),
                        new PersonalCenterSnapshot.HealthCertification(
                                "VERIFIED",
                                "张*",
                                "330***********1234",
                                true),
                        new PersonalCenterSnapshot.Membership(
                                true,
                                1,
                                null,
                                false,
                                38L),
                        new PersonalCenterSnapshot.Capabilities(
                                true,
                                true,
                                true,
                                false,
                                false,
                                true),
                        new PersonalCenterPrivacySettings(
                                true, true, true, true, false, true));
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject(userId.toString())
                        .build();
        when(service.load(userId)).thenReturn(expected);

        PersonalCenterSnapshot actual =
                new PersonalCenterController(service, functionService, accountService)
                        .personalCenter(jwt);

        assertThat(actual).isSameAs(expected);
        verify(service).load(userId);
    }

    @Test
    void updatesPrivacyAndSubmitsFeedbackForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject(userId.toString())
                        .build();
        PersonalCenterPrivacySettings privacy =
                new PersonalCenterPrivacySettings(
                        false, true, false, true, true, false);
        when(functionService.updatePrivacy(userId, privacy))
                .thenReturn(privacy);

        PersonalCenterController controller =
                new PersonalCenterController(service, functionService, accountService);

        assertThat(controller.updatePrivacy(jwt, privacy))
                .isEqualTo(privacy);
        controller.submitFeedback(
                jwt,
                new PersonalCenterController.FeedbackRequest(
                        FeedbackCategory.FEEDBACK,
                        "希望增加深色模式"));

        verify(functionService).updatePrivacy(userId, privacy);
        verify(functionService)
                .submitFeedback(
                        userId,
                        FeedbackCategory.FEEDBACK,
                        "希望增加深色模式");
    }

    @Test
    void requestsPhoneCodeBindsPhoneAndDeactivatesTheAuthenticatedAccount() {
        UUID userId = UUID.randomUUID();
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject(userId.toString())
                        .build();
        PersonalCenterController controller =
                new PersonalCenterController(service, functionService, accountService);
        when(accountService.requestPhoneCode(userId, "13800138000"))
                .thenReturn(new PersonalCenterAccountService.PhoneCodeResult(300));
        when(accountService.bindPhone(userId, "13800138000", "123456"))
                .thenReturn(
                        new PersonalCenterAccountService.PhoneBindingResult(
                                "138****8000", false));

        assertThat(
                        controller.requestPhoneCode(
                                jwt,
                                new PersonalCenterController.PhoneCodeRequest(
                                        "13800138000")))
                .isEqualTo(new PersonalCenterAccountService.PhoneCodeResult(300));
        assertThat(
                        controller.bindPhone(
                                jwt,
                                new PersonalCenterController.PhoneBindingRequest(
                                        "13800138000", "123456")))
                .isEqualTo(
                        new PersonalCenterAccountService.PhoneBindingResult(
                                "138****8000", false));
        controller.deactivateAccount(
                jwt,
                new PersonalCenterController.AccountDeletionRequest(
                        "注销账号"));

        verify(accountService).requestPhoneCode(userId, "13800138000");
        verify(accountService).bindPhone(userId, "13800138000", "123456");
        verify(accountService).deactivateAccount(userId);
    }
}
