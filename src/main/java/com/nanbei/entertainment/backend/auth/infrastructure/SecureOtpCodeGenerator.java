package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.OtpCodeGenerator;
import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class SecureOtpCodeGenerator implements OtpCodeGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }
}
