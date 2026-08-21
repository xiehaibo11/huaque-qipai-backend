package com.nanbei.entertainment.backend.auth.application;

import com.nanbei.entertainment.backend.user.domain.IdentityProvider;

public record ExternalIdentity(IdentityProvider provider, String subject) {}
