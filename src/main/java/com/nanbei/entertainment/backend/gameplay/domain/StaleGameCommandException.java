package com.nanbei.entertainment.backend.gameplay.domain;

/** Raised before an out-of-date command can mutate authoritative state. */
public final class StaleGameCommandException extends RuntimeException {
    private final long expectedRevision;
    private final long currentRevision;

    public StaleGameCommandException(long expectedRevision, long currentRevision) {
        super(
                "expected revision "
                        + expectedRevision
                        + " does not match current revision "
                        + currentRevision);
        this.expectedRevision = expectedRevision;
        this.currentRevision = currentRevision;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public long currentRevision() {
        return currentRevision;
    }
}
