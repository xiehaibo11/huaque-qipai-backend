package com.nanbei.entertainment.backend.membership.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.membership.domain.MembershipProductEntity;
import com.nanbei.entertainment.backend.membership.infrastructure.MembershipProductRepository;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentProductRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class MembershipProductService {
    private final MembershipProductRepository membershipProductRepository;
    private final PaymentProductRepository paymentProductRepository;
    private final ObjectMapper objectMapper;

    public MembershipProductService(
            MembershipProductRepository membershipProductRepository,
            PaymentProductRepository paymentProductRepository,
            ObjectMapper objectMapper) {
        this.membershipProductRepository = membershipProductRepository;
        this.paymentProductRepository = paymentProductRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MembershipProductResponse> listProducts() {
        return membershipProductRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::responseFrom)
                .toList();
    }

    private MembershipProductResponse responseFrom(MembershipProductEntity membershipProduct) {
        PaymentProductEntity paymentProduct =
                paymentProductRepository
                        .findByProductCodeAndEnabledTrue(membershipProduct.getProductCode())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.PAYMENT_PRODUCT_NOT_FOUND,
                                                "会员商品支付配置不存在或已下架"));
        return new MembershipProductResponse(
                membershipProduct.getProductId(),
                membershipProduct.getProductCode(),
                membershipProduct.getPlanCode(),
                paymentProduct.getName(),
                paymentProduct.getAmountMinor(),
                paymentProduct.getCurrency(),
                membershipProduct.getDurationDays(),
                membershipProduct.getGiftValueYuan(),
                membershipProduct.getPriceText(),
                membershipProduct.getDayCostText(),
                membershipProduct.getCardStyle(),
                membershipProduct.getCornerTag(),
                membershipProduct.isSubscription(),
                membershipProduct.getPrivilegesCount(),
                membershipProduct.getDailyGiftValueYuan(),
                membershipProduct.getSortOrder(),
                rewards(membershipProduct));
    }

    private List<MembershipProductReward> rewards(MembershipProductEntity membershipProduct) {
        try {
            JsonNode root = objectMapper.readTree(membershipProduct.getRewards());
            List<MembershipProductReward> rewards = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    rewards.add(
                            new MembershipProductReward(
                                    text(node, "code"),
                                    text(node, "displayName"),
                                    node.path("quantity").asLong(),
                                    text(node, "countText"),
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
