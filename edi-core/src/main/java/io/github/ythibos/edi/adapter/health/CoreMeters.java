package io.github.ythibos.edi.adapter.health;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class CoreMeters {

    public static final String SUPERVISION_ROUND = "edi.supervision.round";
    public static final String WORKER_LAUNCH = "edi.worker.launch";
    public static final String STORE_TRANSACTION = "edi.store.transaction";
    public static final String WAKE_PRINTED = "edi.wake.printed";
    public static final String WAKE_ACKNOWLEDGED = "edi.wake.acknowledged";
    public static final String MEMORY_USED = "edi.memory.used";

    private final MeterRegistry registry;

    public CoreMeters(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder(MEMORY_USED, Runtime.getRuntime(), CoreMeters::memoryInUse)
                .description("The memory the core holds in use, in bytes")
                .baseUnit("bytes")
                .register(registry);
    }

    public MeterRegistry registry() {
        return registry;
    }

    public void supervisionRound(Runnable round) {
        timer(SUPERVISION_ROUND, "How long one supervision round takes").record(round);
    }

    public <T> T workerLaunch(Supplier<T> launch) {
        return timer(WORKER_LAUNCH, "How long a worker's launch takes, from its copy to its typed brief").record(launch);
    }

    public <T> T storeTransaction(Supplier<T> transaction) {
        return timer(STORE_TRANSACTION, "How long one store transaction takes, its wait for the write lock included").record(transaction);
    }

    public void wakePrinted(Duration sinceRaised) {
        timer(WAKE_PRINTED, "How long a wake waits from being raised to being printed to the primary").record(sinceRaised);
    }

    public void wakeAcknowledged(Duration sincePrinted) {
        timer(WAKE_ACKNOWLEDGED, "How long a printed wake waits for the primary to act on it").record(sincePrinted);
    }

    private Timer timer(String name, String description) {
        return Timer.builder(name).description(description).register(registry);
    }

    private static double memoryInUse(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
