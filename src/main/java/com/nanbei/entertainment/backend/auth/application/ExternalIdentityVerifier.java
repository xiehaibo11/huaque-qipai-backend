package com.nanbei.entertainment.backend.auth.application;

import com.nanbei.entertainment.backend.user.domain.IdentityProvider;

public interface ExternalIdentityVerifier {
    boolean supports(IdentityProvider provider);

    ExternalIdentity verify(IdentityProvider provider, String credential);
}
