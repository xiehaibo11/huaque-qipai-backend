package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.GetMobileRequest;
import com.aliyun.dypnsapi20170525.models.GetMobileResponse;
import com.aliyun.dypnsapi20170525.models.GetMobileResponseBody;
import com.aliyun.dypnsapi20170525.models.GetMobileResponseBody.GetMobileResponseBodyGetMobileResultDTO;
import com.aliyun.tea.TeaException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AliyunDypnsClientTest {
    @Test
    void mapsTypedRequestAndResponse() throws Exception {
        Client sdkClient = org.mockito.Mockito.mock(Client.class);
        when(sdkClient.getMobile(any()))
                .thenReturn(
                        new GetMobileResponse()
                                .setBody(
                                        new GetMobileResponseBody()
                                                .setCode("OK")
                                                .setRequestId("request-id")
                                                .setGetMobileResultDTO(
                                                        new GetMobileResponseBodyGetMobileResultDTO()
                                                                .setMobile(
                                                                        "13800138000"))));
        AliyunDypnsClient client =
                new AliyunDypnsClient(sdkClient);

        DypnsClient.Result result =
                client.getMobile(
                        "access-token", "trace-id");

        ArgumentCaptor<GetMobileRequest> request =
                ArgumentCaptor.forClass(GetMobileRequest.class);
        verify(sdkClient).getMobile(request.capture());
        assertThat(request.getValue().getAccessToken())
                .isEqualTo("access-token");
        assertThat(request.getValue().getOutId())
                .isEqualTo("trace-id");
        assertThat(result)
                .isEqualTo(
                        new DypnsClient.Result(
                                "OK",
                                "13800138000",
                                "request-id"));
    }

    @Test
    void mapsEmptyBodyWithoutThrowingNullPointerException()
            throws Exception {
        Client sdkClient = org.mockito.Mockito.mock(Client.class);
        when(sdkClient.getMobile(any()))
                .thenReturn(new GetMobileResponse());
        AliyunDypnsClient client =
                new AliyunDypnsClient(sdkClient);

        assertThat(client.getMobile("access-token", "trace-id"))
                .isEqualTo(
                        new DypnsClient.Result(
                                null, null, null));
    }

    @Test
    void wrapsTeaExceptionWithSafeProviderMetadata()
            throws Exception {
        Client sdkClient = org.mockito.Mockito.mock(Client.class);
        TeaException providerFailure = new TeaException();
        providerFailure.setCode("ServiceUnavailable");
        providerFailure.setData(
                Map.of("RequestId", "request-id"));
        when(sdkClient.getMobile(any()))
                .thenThrow(providerFailure);
        AliyunDypnsClient client =
                new AliyunDypnsClient(sdkClient);

        assertThatThrownBy(
                        () ->
                                client.getMobile(
                                        "secret-access-token",
                                        "trace-id"))
                .isInstanceOfSatisfying(
                        DypnsClient.RequestException.class,
                        exception -> {
                            assertThat(exception.providerCode())
                                    .isEqualTo(
                                            "ServiceUnavailable");
                            assertThat(exception.requestId())
                                    .isEqualTo("request-id");
                            assertThat(exception.getMessage())
                                    .doesNotContain(
                                            "secret-access-token");
                        });
    }
}
