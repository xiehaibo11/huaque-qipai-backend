package com.nanbei.entertainment.backend.wechatpush.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.wechatpush.application.WeChatAuthorizationEvent;
import com.nanbei.entertainment.backend.wechatpush.application.WeChatAuthorizationEventService;
import com.nanbei.entertainment.backend.wechatpush.application.WeChatPushGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class WeChatPushControllerTest {
    @Mock WeChatPushGateway gateway;
    @Mock WeChatAuthorizationEventService eventService;

    @Test
    void challengeReturnsEchoWithoutJsonWrapping() {
        when(gateway.verifyChallenge("signature", "timestamp", "nonce"))
                .thenReturn(true);
        WeChatPushController controller =
                new WeChatPushController(gateway, eventService);

        ResponseEntity<String> response =
                controller.verify(
                        "signature", "timestamp", "nonce", "4375120948345356249");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("4375120948345356249");
    }

    @Test
    void safeModeMessageIsHandledOnlyAfterGatewayVerification() {
        WeChatAuthorizationEvent event =
                new WeChatAuthorizationEvent(
                        "user_info_modified", 1L, "wx-test", "open", null, null);
        when(gateway.decryptEvent("envelope", "signature", "timestamp", "nonce"))
                .thenReturn(event);
        WeChatPushController controller =
                new WeChatPushController(gateway, eventService);

        ResponseEntity<String> response =
                controller.receive(
                        "signature", "timestamp", "nonce", "envelope");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("success");
        verify(eventService).handle(event);
    }
}
