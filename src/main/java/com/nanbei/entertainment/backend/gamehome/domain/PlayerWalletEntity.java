package com.nanbei.entertainment.backend.gamehome.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_wallets")
public class PlayerWalletEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "room_card_centi", nullable = false)
    private long roomCardCenti;

    @Column(name = "room_cards", nullable = false, insertable = false, updatable = false)
    private long roomCards;

    @Column(name = "bound_room_cards", nullable = false)
    private long boundRoomCards;

    @Column(nullable = false)
    private long coins;

    @Column(nullable = false)
    private long diamonds;

    @Column(nullable = false)
    private long coupons;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PlayerWalletEntity() {}

    public PlayerWalletEntity(
            UUID userId,
            long roomCards,
            long boundRoomCards,
            long coins,
            long diamonds) {
        this.userId = userId;
        this.roomCardCenti = roomCards * 100;
        this.roomCards = roomCards;
        this.boundRoomCards = boundRoomCards;
        this.coins = coins;
        this.diamonds = diamonds;
        this.coupons = 0;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public long getRoomCards() {
        return roomCards;
    }

    public long getRoomCardCenti() {
        return roomCardCenti;
    }

    public long getBoundRoomCards() {
        return boundRoomCards;
    }


    public void addCoins(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("coin amount must be positive");
        }
        coins = Math.addExact(coins, amount);
    }

    public long getCoins() {
        return coins;
    }

    public long getDiamonds() {
        return diamonds;
    }

    public long getCoupons() {
        return coupons;
    }

    public void debitDiamonds(long amount) {
        requireAffordable(diamonds, amount, "diamonds");
        diamonds -= amount;
    }

    public void debitRoomCards(long amount) {
        long centiAmount = Math.multiplyExact(amount, 100L);
        debitRoomCardCenti(centiAmount);
    }

    public void debitRoomCardCenti(long amount) {
        requireAffordable(roomCardCenti, amount, "room cards");
        roomCardCenti -= amount;
        roomCards = roomCardCenti / 100L;
    }

    public void debitCoupons(long amount) {
        requireAffordable(coupons, amount, "coupons");
        coupons -= amount;
    }

    public void addDiamonds(long amount) {
        requirePositive(amount, "diamond amount");
        diamonds = Math.addExact(diamonds, amount);
    }

    public void addRoomCards(long amount) {
        requirePositive(amount, "room-card amount");
        roomCardCenti = Math.addExact(roomCardCenti, Math.multiplyExact(amount, 100L));
        roomCards = roomCardCenti / 100L;
    }

    public void addCoupons(long amount) {
        requirePositive(amount, "coupon amount");
        coupons = Math.addExact(coupons, amount);
    }

    private static void requireAffordable(long balance, long amount, String currency) {
        requirePositive(amount, currency + " debit");
        if (balance < amount) {
            throw new IllegalArgumentException("insufficient " + currency);
        }
    }

    private static void requirePositive(long amount, String field) {
        if (amount <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
