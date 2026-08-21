package com.nanbei.entertainment.backend.auth.infrastructure;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.tea.TeaException;
import java.util.Map;

public class AliyunSmsGateway implements SmsGateway {
    private final Client client;

    public AliyunSmsGateway(Client client) {
        this.client = client;
    }

    @Override
    public SendResult send(SendCommand command) {
        SendSmsRequest request =
                new SendSmsRequest()
                        .setPhoneNumbers(command.phoneNumber())
                        .setSignName(command.signName())
                        .setTemplateCode(command.templateCode())
                        .setTemplateParam(command.templateParam());
        try {
            SendSmsResponse response = client.sendSms(request);
            SendSmsResponseBody body = response == null ? null : response.getBody();
            if (body == null) {
                return new SendResult(null, null, null);
            }
            return new SendResult(body.getCode(), body.getMessage(), body.getRequestId());
        } catch (Exception exception) {
            if (exception instanceof TeaException teaException) {
                throw new SmsGatewayRequestException(
                        teaException.getCode(),
                        requestIdFrom(teaException.getData()),
                        teaException);
            }
            throw new SmsGatewayRequestException(
                    exception.getClass().getSimpleName(), null, exception);
        }
    }

    private String requestIdFrom(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object requestId = data.get("RequestId");
        if (requestId == null) {
            requestId = data.get("requestId");
        }
        return requestId == null ? null : requestId.toString();
    }
}
