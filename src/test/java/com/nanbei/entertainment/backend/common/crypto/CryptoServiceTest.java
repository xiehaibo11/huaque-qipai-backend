package com.nanbei.entertainment.backend.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CryptoServiceTest {
    private final CryptoService cryptoService = new CryptoService();

    @Test
    void hashesDeterministicallyAndCreatesDistinctRandomTokens() {
        assertThat(cryptoService.sha256("南北娱乐"))
                .isEqualTo(cryptoService.sha256("南北娱乐"))
                .hasSize(64);
        assertThat(cryptoService.randomToken())
                .isNotEqualTo(cryptoService.randomToken());
    }
}
