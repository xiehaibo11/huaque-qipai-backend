package com.nanbei.entertainment.backend.membership.application;

import com.nanbei.entertainment.backend.membership.domain.MembershipProductEntity;
import com.nanbei.entertainment.backend.membership.infrastructure.MembershipProductRepository;
import com.nanbei.entertainment.backend.payment.application.PaymentFulfillmentHandler;
import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class MembershipPaymentFulfillmentHandler implements PaymentFulfillmentHandler {
    private final MembershipProductRepository membershipProductRepository;
    private final MembershipStatusService membershipStatusService;
    private final MembershipRewardGrantService rewardGrantService;
    private final ObjectMapper objectMapper;

    public MembershipPaymentFulfillmentHandler(
            MembershipProductRepository membershipProductRepository,
            MembershipStatusService membershipStatusService,
            MembershipRewardGrantService rewardGrantService,
            ObjectMapper objectMapper) {
        this.membershipProductRepository = membershipProductRepository;
        this.membershipStatusService = membershipStatusService;
        this.rewardGrantService = rewardGrantService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void fulfill(PaymentOrderEntity order) {
        membershipProductRepository
                .findByProductIdAndActiveTrue(order.getProductId())
                .ifPresent(product -> activateMembership(order, product));
    }

    private void activateMembership(
            PaymentOrderEntity order, MembershipProductEntity product) {
        membershipStatusService.activate(
                order.getUserId(),
                product.getDurationDays(),
                product.isSubscription(),
                order.getId(),
                order.getPaidAt());
        rewardGrantService.grant(
                order.getUserId(),
                MembershipRewardGrantService.SOURCE_PURCHASE,
                order.getId().toString(),
                rewards(product));
    }

    private List<MembershipRewardGrant> rewards(MembershipProductEntity product) {
        try {
            JsonNode root = objectMapper.readTree(product.getRewards());
            List<MembershipRewardGrant> rewards = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    rewards.add(
                            new MembershipRewardGrant(
                                    text(node, "code"),
                                    text(node, "displayName"),
                                    node.path("quantity").asLong(),
                                    node.has("durationDays")
                                            ? node.path("durationDays").asInt()
                                            : null,
                                    text(node, "iconKey")));
                }
            }
            return rewards;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse membership product rewards", exception);
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null ? "" : value.asText();
    }
}
