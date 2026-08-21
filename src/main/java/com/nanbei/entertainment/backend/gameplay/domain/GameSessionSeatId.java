package com.nanbei.entertainment.backend.gameplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class GameSessionSeatId implements Serializable {
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    protected GameSessionSeatId() {}

    public GameSessionSeatId(UUID sessionId, int seatNumber) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        if (seatNumber <= 0) {
            throw new IllegalArgumentException("seatNumber must be positive");
        }
        this.seatNumber = seatNumber;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GameSessionSeatId that)) {
            return false;
        }
        return seatNumber == that.seatNumber && sessionId.equals(that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, seatNumber);
    }
}
