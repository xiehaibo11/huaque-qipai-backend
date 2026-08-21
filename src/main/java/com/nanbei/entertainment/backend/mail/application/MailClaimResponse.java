package com.nanbei.entertainment.backend.mail.application;

import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.util.List;

public record MailClaimResponse(
        List<Long> claimedMailIds, List<MailClaimReward> rewards, ShopWalletResponse wallet) {}
