package io.github.ythibos.edi.application.wake;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.ythibos.edi.adapter.store.WakeQueued;

@Component
public class WakeSignal implements AutoCloseable {

    private final Set<Runnable> waiting = ConcurrentHashMap.newKeySet();
    private final ExecutorService wakers = Executors.newVirtualThreadPerTaskExecutor();

    public void onNextWake(Runnable waiter) {
        waiting.add(waiter);
    }

    public void forget(Runnable waiter) {
        waiting.remove(waiter);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void wakeQueued(WakeQueued queued) {
        List.copyOf(waiting).stream().filter(waiting::remove).forEach(wakers::submit);
    }

    @Override
    public void close() {
        wakers.shutdownNow();
    }
}
