package com.nanbei.entertainment.backend.gameplay.domain;

import java.util.Objects;
import java.util.UUID;

/** Converges every occupied seat to ready for an original auto-ready room. */
public record AutoReadyCommand(UUID actorUserId, long expectedRevision)
        implements GameCommand {
    public AutoReadyCommand {
        Objects.requireNonNull(actorUserId, "actorUserId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
    }

    @Override
    public String type() {
        return "AUTO_READY";
    }
}
