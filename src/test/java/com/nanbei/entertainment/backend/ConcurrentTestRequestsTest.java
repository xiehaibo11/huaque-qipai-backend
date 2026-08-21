package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ConcurrentTestRequestsTest {
    @Test
    void timesOutAndCancelsUnfinishedRequests() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();

        assertThatThrownBy(
                        () ->
                                ConcurrentTestRequests.run(
                                        1,
                                        () -> {
                                            try {
                                                new CountDownLatch(1).await();
                                                return "unreachable";
                                            } catch (InterruptedException exception) {
                                                interrupted.set(true);
                                                throw exception;
                                            }
                                        },
                                        () -> {},
                                        Duration.ofMillis(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");

        Instant deadline = Instant.now().plusSeconds(1);
        while (!interrupted.get() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        assertThat(interrupted).isTrue();
    }
}
