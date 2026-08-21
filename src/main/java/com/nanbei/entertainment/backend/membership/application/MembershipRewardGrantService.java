package com.nanbei.entertainment.backend.membership.application;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.membership.domain.MembershipRewardGrantEntity;
import com.nanbei.entertainment.backend.membership.infrastructure.MembershipRewardGrantRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class MembershipRewardGrantService {
    static final String SOURCE_DAILY_GIFT = "MEMBERSHIP_DAILY_GIFT";
    static final String SOURCE_PURCHASE = "MEMBERSHIP_PURCHASE";

    private final PlayerWalletRepository walletRepository;
    private final MembershipRewardGrantRepository rewardGrantRepository;
    private final ObjectMapper objectMapper;

    public MembershipRewardGrantService(
            PlayerWalletRepository walletRepository,
            MembershipRewardGrantRepository rewardGrantRepository,
            ObjectMapper objectMapper) {
        this.walletRepository = walletRepository;
        this.rewardGrantRepository = rewardGrantRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PlayerWalletEntity grant(
            UUID userId,
            String sourceType,
            String sourceId,
            List<MembershipRewardGrant> rewards) {
        PlayerWalletEntity wallet =
                walletRepository
                        .findById(userId)
                        .orElseGet(() -> new PlayerWalletEntity(userId, 0, 0, 0, 0));
        for (MembershipRewardGrant reward : rewards) {
            if (alreadyGranted(userId, sourceType, sourceId, reward)) {
                continue;
            }
            if ("COIN".equals(reward.code())) {
                wallet.addCoins(reward.quantity());
            }
            rewardGrantRepository.save(
                    new MembershipRewardGrantEntity(
                            userId,
                            sourceType,
                            sourceId,
                            reward.code(),
                            reward.displayName(),
                            reward.quantity(),
                            reward.durationDays(),
                            metadata(reward)));
        }
        return walletRepository.save(wallet);
    }

    private boolean alreadyGranted(
            UUID userId,
            String sourceType,
            String sourceId,
            MembershipRewardGrant reward) {
        return rewardGrantRepository
                .existsByUserIdAndSourceTypeAndSourceIdAndRewardCodeAndDisplayName(
                        userId,
                        sourceType,
                        sourceId,
                        reward.code(),
                        reward.displayName());
    }

    private String metadata(MembershipRewardGrant reward) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "iconKey", reward.iconKey() == null ? "" : reward.iconKey()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize membership reward metadata", exception);
        }
    }
}
