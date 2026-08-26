package com.nanbei.entertainment.backend.auth.application;

import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record ExternalIdentity(
        IdentityProvider provider,
        String subject,
        List<String> aliases,
        String displayName,
        byte[] avatarBytes,
        String avatarContentType) {
    public ExternalIdentity {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public ExternalIdentity(
            IdentityProvider provider,
            String subject,
            String displayName,
            byte[] avatarBytes,
            String avatarContentType) {
        this(provider, subject, List.of(), displayName, avatarBytes, avatarContentType);
    }

    public ExternalIdentity(IdentityProvider provider, String subject) {
        this(provider, subject, List.of(), null, null, null);
    }

    public List<String> subjects() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        unique.add(subject);
        unique.addAll(aliases);
        return new ArrayList<>(unique);
    }
}
