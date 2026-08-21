package com.nanbei.entertainment.backend.mail.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.mail.application.MailClaimResponse;
import com.nanbei.entertainment.backend.mail.application.MailDeletedCountResponse;
import com.nanbei.entertainment.backend.mail.application.MailDetailResponse;
import com.nanbei.entertainment.backend.mail.application.MailListResponse;
import com.nanbei.entertainment.backend.mail.application.MailService;
import com.nanbei.entertainment.backend.mail.application.MailSummaryResponse;
import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class MailControllerTest {
    @Mock MailService mailService;

    @Test
    void returnsSummaryForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        MailSummaryResponse expected = new MailSummaryResponse(2, 1);
        when(mailService.summary(userId)).thenReturn(expected);

        assertThat(controller().summary(jwt(userId))).isSameAs(expected);
    }

    @Test
    void listsMailsForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        MailListResponse expected = new MailListResponse(List.of());
        when(mailService.list(userId)).thenReturn(expected);

        assertThat(controller().list(jwt(userId))).isSameAs(expected);
    }

    @Test
    void loadsDetailForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        MailDetailResponse expected =
                new MailDetailResponse(
                        7L, "系统", "标题", "内容", null, null, true, false, List.of());
        when(mailService.detail(userId, 7L)).thenReturn(expected);

        assertThat(controller().detail(jwt(userId), 7L)).isSameAs(expected);
    }

    @Test
    void marksAllReadForTheJwtSubject() {
        UUID userId = UUID.randomUUID();

        controller().readAll(jwt(userId));

        verify(mailService).readAll(userId);
    }

    @Test
    void deletesRequestedMailIdsForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        MailDeletedCountResponse expected = new MailDeletedCountResponse(2);
        when(mailService.delete(userId, List.of(1L, 2L))).thenReturn(expected);

        MailDeletedCountResponse actual =
                controller()
                        .delete(jwt(userId), new MailController.MailIdsRequest(List.of(1L, 2L)));

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void claimsRequestedMailIdsForTheJwtSubject() {
        UUID userId = UUID.randomUUID();
        MailClaimResponse expected =
                new MailClaimResponse(
                        List.of(1L), List.of(), new ShopWalletResponse(0, 0, 0, 0));
        when(mailService.claim(userId, List.of(1L))).thenReturn(expected);

        MailClaimResponse actual =
                controller().claim(jwt(userId), new MailController.MailIdsRequest(List.of(1L)));

        assertThat(actual).isSameAs(expected);
    }

    private MailController controller() {
        return new MailController(mailService);
    }

    private static Jwt jwt(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
    }
}
