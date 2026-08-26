package com.nanbei.entertainment.backend.legal.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nanbei.entertainment.backend.common.config.SecurityProperties;
import com.nanbei.entertainment.backend.common.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = LoginAgreementController.class,
        properties = "nanbei.security.jwt-secret=01234567890123456789012345678901")
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
@EnableConfigurationProperties(SecurityProperties.class)
class LoginAgreementControllerWebMvcTest {
    @Autowired MockMvc mockMvc;

    @Test
    void exposesNanbeiLoginAgreementsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/public/login-agreements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorName").value("南北娱乐"))
                .andExpect(jsonPath("$.agreements.length()").value(2))
                .andExpect(jsonPath("$.agreements[0].type").value("SERVER"))
                .andExpect(jsonPath("$.agreements[0].title").value("用户服务协议"))
                .andExpect(
                        jsonPath("$.agreements[0].url")
                                .value("https://www.nanbeiyule.com/terms"))
                .andExpect(jsonPath("$.agreements[1].type").value("PRIVACY"))
                .andExpect(jsonPath("$.agreements[1].title").value("隐私保护政策"))
                .andExpect(
                        jsonPath("$.agreements[1].url")
                                .value("https://www.nanbeiyule.com/privacy"));
    }
}
