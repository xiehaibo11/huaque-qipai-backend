package com.nanbei.entertainment.backend.common.security;

import com.nanbei.entertainment.backend.common.config.SecurityProperties;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final SecurityProperties properties;

    public JwtTokenService(SecurityProperties properties) {
        this.properties = properties;
        if (properties.jwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
    }

    public String createAccessToken(UserEntity user) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject(user.getId().toString())
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plus(properties.accessTokenTtl())))
                            .claim("scope", "user")
                            .claim("displayName", user.getDisplayName())
                            .claim("authVersion", user.getAuthVersion())
                            .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(properties.jwtSecret()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    public long expiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
