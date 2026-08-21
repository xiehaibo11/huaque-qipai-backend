package com.nanbei.entertainment.backend.realname.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.realname.application.RealNameService;
import com.nanbei.entertainment.backend.realname.application.RealNameStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class RealNameControllerTest {
    @Mock RealNameService realNameService;

    @Test
    void loadsRealNameStatusForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        RealNameStatus expected =
                RealNameStatus.unverified(false);
        Jwt jwt = jwt(userId);
        when(realNameService.status(userId)).thenReturn(expected);

        RealNameStatus actual =
                new RealNameController(realNameService).status(jwt);

        assertThat(actual).isSameAs(expected);
        verify(realNameService).status(userId);
    }

    @Test
    void verifiesManuallyForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        RealNameStatus expected =
                new RealNameStatus(
                        "VERIFIED",
                        "张**",
                        "1101**********1237",
                        Instant.now(),
                        false);
        Jwt jwt = jwt(userId);
        when(realNameService.verifyManually(
                        userId, "张测试", "110101199001011237"))
                .thenReturn(expected);

        RealNameStatus actual =
                new RealNameController(realNameService)
                        .verify(
                                jwt,
                                new RealNameController.RealNameVerifyRequest(
                                        "张测试", "110101199001011237"));

        assertThat(actual).isSameAs(expected);
        verify(realNameService)
                .verifyManually(userId, "张测试", "110101199001011237");
    }

    @Test
    void verifiesWithAlipayForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        RealNameStatus expected =
                new RealNameStatus(
                        "VERIFIED",
                        "张**",
                        "1101**********1237",
                        Instant.now(),
                        false);
        Jwt jwt = jwt(userId);
        when(realNameService.verifyWithAlipay(userId, "auth-code"))
                .thenReturn(expected);

        RealNameStatus actual =
                new RealNameController(realNameService)
                        .verifyWithAlipay(
                                jwt,
                                new RealNameController.AlipayVerifyRequest(
                                        "auth-code"));

        assertThat(actual).isSameAs(expected);
        verify(realNameService).verifyWithAlipay(userId, "auth-code");
    }

    private static Jwt jwt(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
    }
}
