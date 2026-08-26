package com.nanbei.entertainment.backend.legal.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/login-agreements")
public class LoginAgreementController {
    private static final String OPERATOR_NAME = "南北娱乐";
    private static final String VERSION = "2026-08-23";

    @GetMapping
    LoginAgreementResponse agreements() {
        return new LoginAgreementResponse(
                OPERATOR_NAME,
                VERSION,
                List.of(
                        new Agreement(
                                "SERVER",
                                "用户服务协议",
                                "https://www.nanbeiyule.com/terms"),
                        new Agreement(
                                "PRIVACY",
                                "隐私保护政策",
                                "https://www.nanbeiyule.com/privacy")));
    }

    record LoginAgreementResponse(
            String operatorName, String version, List<Agreement> agreements) {
        LoginAgreementResponse {
            agreements = List.copyOf(agreements);
        }
    }

    record Agreement(String type, String title, String url) {}
}
