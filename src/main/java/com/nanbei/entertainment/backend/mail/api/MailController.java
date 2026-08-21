package com.nanbei.entertainment.backend.mail.api;

import com.nanbei.entertainment.backend.mail.application.MailClaimResponse;
import com.nanbei.entertainment.backend.mail.application.MailDeletedCountResponse;
import com.nanbei.entertainment.backend.mail.application.MailDetailResponse;
import com.nanbei.entertainment.backend.mail.application.MailListResponse;
import com.nanbei.entertainment.backend.mail.application.MailMarkedReadResponse;
import com.nanbei.entertainment.backend.mail.application.MailService;
import com.nanbei.entertainment.backend.mail.application.MailSummaryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mails")
public class MailController {
    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping("/summary")
    MailSummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return mailService.summary(userId(jwt));
    }

    @GetMapping
    MailListResponse list(@AuthenticationPrincipal Jwt jwt) {
        return mailService.list(userId(jwt));
    }

    @GetMapping("/{mailId}")
    MailDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long mailId) {
        return mailService.detail(userId(jwt), mailId);
    }

    @PostMapping("/read-all")
    MailMarkedReadResponse readAll(@AuthenticationPrincipal Jwt jwt) {
        return mailService.readAll(userId(jwt));
    }

    @PostMapping("/delete")
    MailDeletedCountResponse delete(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MailIdsRequest request) {
        return mailService.delete(userId(jwt), request.mailIds());
    }

    @PostMapping("/claim")
    MailClaimResponse claim(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MailIdsRequest request) {
        return mailService.claim(userId(jwt), request.mailIds());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record MailIdsRequest(@NotNull List<Long> mailIds) {}
}
