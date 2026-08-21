package com.nanbei.entertainment.backend.auth.infrastructure;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.GetMobileRequest;
import com.aliyun.dypnsapi20170525.models.GetMobileResponse;
import com.aliyun.dypnsapi20170525.models.GetMobileResponseBody;
import com.aliyun.dypnsapi20170525.models.GetMobileResponseBody.GetMobileResponseBodyGetMobileResultDTO;
import com.aliyun.tea.TeaException;
import java.util.Map;

public final class AliyunDypnsClient implements DypnsClient {
    private final Client client;

    public AliyunDypnsClient(Client client) {
        this.client = client;
    }

    @Override
    public Result getMobile(
            String accessToken, String outId) {
        GetMobileRequest request =
                new GetMobileRequest()
                        .setAccessToken(accessToken)
                        .setOutId(outId);
        try {
            GetMobileResponse response =
                    client.getMobile(request);
            GetMobileResponseBody body =
                    response == null ? null : response.getBody();
            if (body == null) {
                return new Result(null, null, null);
            }
            GetMobileResponseBodyGetMobileResultDTO result =
                    body.getGetMobileResultDTO();
            return new Result(
                    body.getCode(),
                    result == null ? null : result.getMobile(),
                    body.getRequestId());
        } catch (Exception exception) {
            if (exception instanceof TeaException teaException) {
                throw new RequestException(
                        teaException.getCode(),
                        requestIdFrom(teaException.getData()),
                        teaException);
            }
            throw new RequestException(
                    exception.getClass().getSimpleName(),
                    null,
                    exception);
        }
    }

    private static String requestIdFrom(
            Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object requestId = data.get("RequestId");
        if (requestId == null) {
            requestId = data.get("requestId");
        }
        return requestId == null
                ? null
                : requestId.toString();
    }
}
