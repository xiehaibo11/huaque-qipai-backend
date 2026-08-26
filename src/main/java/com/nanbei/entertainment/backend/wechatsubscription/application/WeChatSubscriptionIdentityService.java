package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WeChatSubscriptionIdentityService {
    private final UserIdentityRepository identityRepository;
    private final WeChatProperties properties;
    private final CryptoService cryptoService;

    public WeChatSubscriptionIdentityService(
            UserIdentityRepository identityRepository,
            WeChatProperties properties,
            CryptoService cryptoService) {
        this.identityRepository = identityRepository;
        this.properties = properties;
        this.cryptoService = cryptoService;
    }

    public WeChatSubscriptionIdentity requireCurrent(UUID userId) {
        String prefix = prefix();
        UserIdentityEntity identity =
                identityRepository.findByUser_IdOrderByCreatedAtAsc(userId).stream()
                        .filter(item -> item.getProvider() == IdentityProvider.WECHAT)
                        .filter(item -> item.getProviderSubject().startsWith(prefix))
                        .reduce((first, latest) -> latest)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_PROVIDER_UNAVAILABLE,
                                                "当前账号缺少微信授权身份"));
        String subject = identity.getProviderSubject();
        return new WeChatSubscriptionIdentity(
                subject, subject.substring(prefix.length()), cryptoService.sha256(subject));
    }

    public String subjectHash(String openId) {
        if (openId == null || openId.isBlank()) {
            throw invalid();
        }
        return cryptoService.sha256(prefix() + openId.trim());
    }

    private String prefix() {
        if (properties.appId() == null || properties.appId().isBlank()) {
            throw new ApiException(
                    ErrorCode.AUTH_PROVIDER_UNAVAILABLE, "微信服务尚未配置");
        }
        return "appid:" + properties.appId().trim() + ":openid:";
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "微信订阅授权结果无效");
    }
}
