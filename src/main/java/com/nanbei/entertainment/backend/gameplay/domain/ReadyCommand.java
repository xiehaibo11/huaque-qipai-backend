package com.nanbei.entertainment.backend.gameplay.domain;

import java.util.Objects;
import java.util.UUID;

/** Changes one waiting-room seat's ready state. */
public record ReadyCommand(UUID actorUserId, long expectedRevision, boolean ready)
        implements GameCommand {
    public ReadyCommand(UUID actorUserId, long expectedRevision) {
        this(actorUserId, expectedRevision, true);
    }

    public ReadyCommand {
        Objects.requireNonNull(actorUserId, "actorUserId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
    }

    @Override
    public String type() {
        return ready ? "READY" : "UNREADY";
    }
}
