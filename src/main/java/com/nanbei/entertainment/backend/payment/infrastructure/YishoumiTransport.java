package com.nanbei.entertainment.backend.payment.infrastructure;

import java.net.URI;
import java.util.Map;

public interface YishoumiTransport {
    String postJson(URI endpoint, Map<String, ?> fields);
}
