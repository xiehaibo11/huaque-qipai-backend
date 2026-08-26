package com.nanbei.entertainment.backend.mail.application;

import java.util.List;

public record MailDeletedCountResponse(long deletedCount, List<Long> deletedMailIds) {
    public MailDeletedCountResponse {
        deletedMailIds = List.copyOf(deletedMailIds);
    }

    public MailDeletedCountResponse(long deletedCount) {
        this(deletedCount, List.of());
    }
}
