package com.nanbei.entertainment.backend.mail.application;

import java.util.List;

public record MailListResponse(List<MailListItem> mails, int page, boolean hasMore) {
    public MailListResponse {
        mails = List.copyOf(mails);
    }

    public MailListResponse(List<MailListItem> mails) {
        this(mails, 1, false);
    }
}
