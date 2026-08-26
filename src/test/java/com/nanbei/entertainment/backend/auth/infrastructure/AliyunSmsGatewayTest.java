package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.tea.TeaException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AliyunSmsGatewayTest {
    @Test
    void mapsCommandAndResponse() throws Exception {
        Client client = org.mockito.Mockito.mock(Client.class);
        when(client.sendSms(any()))
                .thenReturn(
                        new SendSmsResponse()
                                .setBody(
                                        new SendSmsResponseBody()
                                                .setCode("OK")
                                                .setMessage("OK")
                                                .setRequestId("request-1")));
        AliyunSmsGateway gateway = new AliyunSmsGateway(client);

        SmsGateway.SendResult result =
                gateway.send(
                        new SmsGateway.SendCommand(
                                "13800138000",
                                "南北娱乐",
                                "SMS_123456789",
                                "{\"code\":\"123456\"}"));

        ArgumentCaptor<com.aliyun.dysmsapi20170525.models.SendSmsRequest> request =
                ArgumentCaptor.forClass(
                        com.aliyun.dysmsapi20170525.models.SendSmsRequest.class);
        verify(client).sendSms(request.capture());
        assertThat(request.getValue().getPhoneNumbers()).isEqualTo("13800138000");
        assertThat(request.getValue().getSignName()).isEqualTo("南北娱乐");
        assertThat(request.getValue().getTemplateCode()).isEqualTo("SMS_123456789");
        assertThat(request.getValue().getTemplateParam()).isEqualTo("{\"code\":\"123456\"}");
        assertThat(result).isEqualTo(new SmsGateway.SendResult("OK", "OK", "request-1"));
    }

    @Test
    void preservesSafeProviderMetadataWhenSdkThrows() throws Exception {
        Client client = org.mockito.Mockito.mock(Client.class);
        TeaException providerFailure = new TeaException();
        providerFailure.setCode("InvalidAccessKeyId.NotFound");
        providerFailure.setData(Map.of("RequestId", "request-2"));
        when(client.sendSms(any())).thenThrow(providerFailure);
        AliyunSmsGateway gateway = new AliyunSmsGateway(client);

        assertThatThrownBy(
                        () ->
                                gateway.send(
                                        new SmsGateway.SendCommand(
                                                "13800138000",
                                                "南北娱乐",
                                                "SMS_123456789",
                                                "{\"code\":\"123456\"}")))
                .isInstanceOfSatisfying(
                        SmsGatewayRequestException.class,
                        exception -> {
                            assertThat(exception.providerCode())
                                    .isEqualTo("InvalidAccessKeyId.NotFound");
                            assertThat(exception.requestId()).isEqualTo("request-2");
                            assertThat(exception.getMessage())
                                    .doesNotContain("13800138000", "123456");
                        });
    }
}
