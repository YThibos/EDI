package io.github.ythibos.edi.application.health;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.HealthSamples;
import io.github.ythibos.edi.application.port.Clock;

@Component
public class RecordCoreHealth {

    private final CoreMeasures measures;
    private final HealthSamples samples;
    private final Duration kept;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RecordCoreHealth(CoreMeasures measures, HealthSamples samples, @Value("${edi.health.kept:P35D}") Duration kept, Clock clock,
            TransactionTemplate transaction) {
        this.measures = measures;
        this.samples = samples;
        this.kept = kept;
        this.clock = clock;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${edi.health.sample-every:PT10M}", initialDelayString = "${edi.health.sample-every:PT10M}")
    public void record() {
        Instant now = clock.now();
        transaction.executeWithoutResult(_ -> {
            samples.record(measures.taken(), now);
            samples.forgetBefore(now.minus(kept));
        });
    }
}
