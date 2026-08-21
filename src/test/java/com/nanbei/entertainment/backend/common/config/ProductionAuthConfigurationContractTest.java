package com.nanbei.entertainment.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionAuthConfigurationContractTest {
    @Test
    void productionComposePassesOneTapConfigurationExplicitly()
            throws IOException {
        String compose = read("compose.production.yml");

        assertThat(compose)
                .contains(
                        "ALIYUN_ONE_TAP_ENABLED: ${ALIYUN_ONE_TAP_ENABLED:-false}",
                        "ALIYUN_ONE_TAP_REGION_ID: ${ALIYUN_ONE_TAP_REGION_ID:-cn-hangzhou}",
                        "ALIYUN_ONE_TAP_ENDPOINT: ${ALIYUN_ONE_TAP_ENDPOINT:-dypnsapi.aliyuncs.com}");
    }

    @Test
    void productionEnvironmentExampleKeepsOneTapDisabledByDefault()
            throws IOException {
        String environment = read(".env.production.example");

        assertThat(environment)
                .contains(
                        "ALIYUN_ONE_TAP_ENABLED=false",
                        "ALIYUN_ONE_TAP_REGION_ID=cn-hangzhou",
                        "ALIYUN_ONE_TAP_ENDPOINT=dypnsapi.aliyuncs.com");
    }

    @Test
    void productionComposePassesRealNameConfigurationExplicitly()
            throws IOException {
        String compose = read("compose.production.yml");

        assertThat(compose)
                .contains(
                        "REALNAME_HMAC_SECRET: ${REALNAME_HMAC_SECRET:?REALNAME_HMAC_SECRET is required}",
                        "ALIYUN_REALNAME_ENABLED: ${ALIYUN_REALNAME_ENABLED:-false}",
                        "ALIPAY_REALNAME_ENABLED: ${ALIPAY_REALNAME_ENABLED:-false}");
    }

    @Test
    void productionEnvironmentExampleKeepsRealNameDisabledByDefault()
            throws IOException {
        String environment = read(".env.production.example");

        assertThat(environment)
                .contains(
                        "REALNAME_HMAC_SECRET=replace-with-a-random-realname-hmac-secret",
                        "ALIYUN_REALNAME_ENABLED=false",
                        "ALIPAY_REALNAME_ENABLED=false");
    }

    private static String read(String fileName) throws IOException {
        Path workingDirectory =
                Path.of(System.getProperty("user.dir"));
        Path repositoryRoot =
                Files.isDirectory(workingDirectory.resolve("backend/src/main"))
                        ? workingDirectory
                        : workingDirectory.getParent();
        return Files.readString(repositoryRoot.resolve(fileName));
    }
}
