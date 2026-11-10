package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.testing.Patience;

class OneTransactionAtATimeTest {

    @TempDir
    private Path directory;

    @Test
    void letsTheTransactionHoldingTheStoreFinishWhileMoreVirtualThreadsWaitThanThereAreCarriers() throws Exception {
        OneTransactionAtATime transactions = new OneTransactionAtATime(StoreFile.openedAt(directory.resolve("edi.db")));
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        JdbcClient jdbc = JdbcClient.create(transactions.getDataSource());
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        int waiters = 2 * Runtime.getRuntime().availableProcessors();
        CountDownLatch waiting = new CountDownLatch(waiters);

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> holder = threads.submit(() -> transaction.execute(_ -> {
                holding.countDown();
                awaited(released);
                return answerFrom(jdbc);
            }));
            awaited(holding);
            List<Future<Integer>> queued = IntStream.range(0, waiters).mapToObj(_ -> threads.submit(() -> {
                waiting.countDown();
                return transaction.execute(_ -> answerFrom(jdbc));
            })).toList();
            awaited(waiting);
            released.countDown();

            assertThat(answerOf(holder)).isEqualTo(1);
            for (Future<Integer> waiter : queued) {
                assertThat(answerOf(waiter)).isEqualTo(1);
            }
        }
    }

    private static void awaited(CountDownLatch latch) {
        try {
            assertThat(latch.await(Patience.FOR_REAL_WORK.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static Integer answerFrom(JdbcClient jdbc) {
        return jdbc.sql("select 1").query(Integer.class).single();
    }

    private static Integer answerOf(Future<Integer> transaction) throws InterruptedException, ExecutionException, TimeoutException {
        return transaction.get(Patience.FOR_REAL_WORK.toMillis(), TimeUnit.MILLISECONDS);
    }
}
