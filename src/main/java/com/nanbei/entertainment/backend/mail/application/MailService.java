package com.nanbei.entertainment.backend.mail.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.mail.domain.MailAttachment;
import com.nanbei.entertainment.backend.mail.domain.MailEntity;
import com.nanbei.entertainment.backend.mail.domain.MailRewardType;
import com.nanbei.entertainment.backend.mail.infrastructure.MailRepository;
import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class MailService {
    private final MailRepository mailRepository;
    private final PlayerWalletRepository walletRepository;
    private final ObjectMapper objectMapper;

    public MailService(
            MailRepository mailRepository,
            PlayerWalletRepository walletRepository,
            ObjectMapper objectMapper) {
        this.mailRepository = mailRepository;
        this.walletRepository = walletRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MailSummaryResponse summary(UUID userId) {
        List<MailEntity> mails = mailRepository.findVisible(userId, Instant.now());
        long unread = mails.stream().filter(mail -> mail.getReadAt() == null).count();
        long award =
                mails.stream()
                        .filter(mail -> mail.getClaimedAt() == null && hasAttachments(mail))
                        .count();
        return new MailSummaryResponse(unread, award);
    }

    @Transactional(readOnly = true)
    public MailListResponse list(UUID userId) {
        List<MailListItem> items =
                mailRepository.findVisible(userId, Instant.now()).stream()
                        .map(this::toListItem)
                        .toList();
        return new MailListResponse(items);
    }

    @Transactional
    public MailDetailResponse detail(UUID userId, long mailId) {
        MailEntity mail =
                mailRepository
                        .findByIdAndUserId(mailId, userId)
                        .filter(candidate -> candidate.getDeletedAt() == null)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.MAIL_NOT_FOUND, "邮件不存在"));
        mail.markRead(Instant.now());
        return new MailDetailResponse(
                mail.getId(),
                mail.getSender(),
                mail.getTitle(),
                mail.getContent(),
                mail.getSendAt(),
                mail.getExpireAt(),
                true,
                mail.getClaimedAt() != null,
                attachmentsOf(mail).stream().map(this::toAttachmentItem).toList());
    }

    @Transactional
    public MailMarkedReadResponse readAll(UUID userId) {
        return new MailMarkedReadResponse(mailRepository.markAllRead(userId, Instant.now()));
    }

    @Transactional
    public MailDeletedCountResponse delete(UUID userId, List<Long> mailIds) {
        if (mailIds.isEmpty()) {
            return new MailDeletedCountResponse(0);
        }
        Instant now = Instant.now();
        long deleted = 0;
        for (MailEntity mail : mailRepository.findByUserIdAndIdIn(userId, mailIds)) {
            if (mail.getDeletedAt() != null) {
                continue;
            }
            if (mail.getClaimedAt() == null && hasAttachments(mail)) {
                continue;
            }
            mail.markDeleted(now);
            deleted++;
        }
        return new MailDeletedCountResponse(deleted);
    }

    @Transactional
    public MailClaimResponse claim(UUID userId, List<Long> mailIds) {
        Instant now = Instant.now();
        List<Long> claimedMailIds = new ArrayList<>();
        EnumMap<MailRewardType, Long> totals = new EnumMap<>(MailRewardType.class);
        PlayerWalletEntity wallet = null;
        if (!mailIds.isEmpty()) {
            for (MailEntity mail : mailRepository.findLockedByUserIdAndIdIn(userId, mailIds)) {
                if (!claimable(mail, now)) {
                    continue;
                }
                if (wallet == null) {
                    wallet = lockedWallet(userId);
                }
                for (MailAttachment attachment : attachmentsOf(mail)) {
                    credit(wallet, attachment, totals);
                }
                mail.markClaimed(now);
                claimedMailIds.add(mail.getId());
            }
            if (wallet != null) {
                walletRepository.save(wallet);
            }
        }
        return new MailClaimResponse(
                claimedMailIds, aggregatedRewards(totals), walletView(userId, wallet));
    }

    private boolean claimable(MailEntity mail, Instant now) {
        return mail.getDeletedAt() == null
                && mail.getClaimedAt() == null
                && hasAttachments(mail)
                && !mail.isExpired(now);
    }

    private PlayerWalletEntity lockedWallet(UUID userId) {
        return walletRepository
                .findLockedByUserId(userId)
                .orElseGet(
                        () ->
                                walletRepository.save(
                                        new PlayerWalletEntity(userId, 0, 0, 0, 0)));
    }

    private void credit(
            PlayerWalletEntity wallet,
            MailAttachment attachment,
            EnumMap<MailRewardType, Long> totals) {
        MailRewardType type = rewardTypeOf(attachment.rewardType());
        switch (type) {
            case COIN -> wallet.addCoins(attachment.amount());
            case DIAMOND -> wallet.addDiamonds(attachment.amount());
            case ROOM_CARD -> wallet.addRoomCards(attachment.amount());
        }
        totals.merge(type, attachment.amount(), Long::sum);
    }

    private static MailRewardType rewardTypeOf(String rewardType) {
        try {
            return MailRewardType.valueOf(rewardType);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported mail attachment reward type", exception);
        }
    }

    private ShopWalletResponse walletView(UUID userId, PlayerWalletEntity wallet) {
        if (wallet != null) {
            return ShopWalletResponse.from(wallet);
        }
        return walletRepository
                .findById(userId)
                .map(ShopWalletResponse::from)
                .orElseGet(() -> new ShopWalletResponse(0, 0, 0, 0));
    }

    private static List<MailClaimReward> aggregatedRewards(
            EnumMap<MailRewardType, Long> totals) {
        return totals.entrySet().stream()
                .map(entry -> new MailClaimReward(entry.getKey().name(), entry.getValue()))
                .toList();
    }

    private MailListItem toListItem(MailEntity mail) {
        return new MailListItem(
                mail.getId(),
                mail.getTitle(),
                mail.getIntro(),
                mail.getSender(),
                hasAttachments(mail),
                mail.getReadAt() != null,
                mail.getClaimedAt() != null,
                mail.getSendAt(),
                mail.getExpireAt());
    }

    private MailAttachmentItem toAttachmentItem(MailAttachment attachment) {
        return new MailAttachmentItem(
                attachment.icon(),
                attachment.rewardType(),
                attachment.amount(),
                attachment.description());
    }

    private boolean hasAttachments(MailEntity mail) {
        return !attachmentsOf(mail).isEmpty();
    }

    private List<MailAttachment> attachmentsOf(MailEntity mail) {
        String json = mail.getAttachments();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<MailAttachment> attachments = new ArrayList<>();
            for (JsonNode node : root) {
                attachments.add(
                        new MailAttachment(
                                node.path("icon").asText(""),
                                node.path("rewardType").asText(""),
                                node.path("amount").asLong(),
                                node.path("description").asText("")));
            }
            return attachments;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse mail attachments", exception);
        }
    }
}
