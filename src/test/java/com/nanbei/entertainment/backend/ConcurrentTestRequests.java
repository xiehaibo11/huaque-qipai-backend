package com.nanbei.entertainment.backend;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ConcurrentTestRequests {
    private ConcurrentTestRequests() {}

    static <T> List<T> run(
            int count,
            Callable<T> operation,
            ThrowingRunnable duringExecution,
            Duration completionTimeout)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures =
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(
                                ignored ->
                                        executor.submit(
                                                () -> {
                                                    start.await();
                                                    return operation.call();
                                                }))
                        .toList();
        try {
            long deadline =
                    System.nanoTime() + completionTimeout.toNanos();
            start.countDown();
            duringExecution.run();
            List<T> results = new ArrayList<>(count);
            for (Future<T> future : futures) {
                results.add(await(future, deadline));
            }
            return List.copyOf(results);
        } catch (Error error) {
            futures.forEach(future -> future.cancel(true));
            throw error;
        } catch (Exception exception) {
            futures.forEach(future -> future.cancel(true));
            throw exception;
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> T await(Future<T> future, long deadline) {
        try {
            long remainingNanos = Math.max(1, deadline - System.nanoTime());
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "Concurrent request timed out", exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Concurrent request did not complete", exception);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
