package com.nanbei.entertainment.backend.gameplay.domain;

import java.util.UUID;

/** A user intent evaluated against one exact game-state revision. */
public interface GameCommand {
    UUID actorUserId();

    long expectedRevision();

    String type();

    default void requireRevision(long currentRevision) {
        if (expectedRevision() != currentRevision) {
            throw new StaleGameCommandException(expectedRevision(), currentRevision);
        }
    }
}
