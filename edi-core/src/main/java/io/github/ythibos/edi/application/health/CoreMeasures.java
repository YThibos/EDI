package io.github.ythibos.edi.application.health;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.health.CoreMeters;
import io.github.ythibos.edi.adapter.store.HealthSamples.HealthSample;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;

@Component
public class CoreMeasures {

    public static final String MEMORY_IN_USE = "memory in use";

    private static final String REQUESTS = "http.server.requests";
    private static final Map<String, String> MEASURE_OF_TIMER = Map.of(
            CoreMeters.WAKE_PRINTED, "wake raised to printed",
            CoreMeters.WAKE_ACKNOWLEDGED, "wake printed to acknowledged",
            CoreMeters.STORE_TRANSACTION, "store transaction",
            CoreMeters.SUPERVISION_ROUND, "supervision round",
            CoreMeters.WORKER_LAUNCH, "worker launch");

    private final CoreMeters meters;
    private final Map<Meter.Id, Taken> takenSoFar = new HashMap<>();

    public CoreMeasures(CoreMeters meters) {
        this.meters = meters;
    }

    public synchronized List<HealthSample> taken() {
        List<Reading> readings = readings();
        readings.forEach(reading -> takenSoFar.put(reading.timer(), reading.now()));
        return samplesOf(readings);
    }

    public synchronized List<HealthSample> pending() {
        return samplesOf(readings());
    }

    private List<Reading> readings() {
        return meters.registry().getMeters().stream()
                .filter(Timer.class::isInstance)
                .map(Timer.class::cast)
                .filter(timer -> MEASURE_OF_TIMER.containsKey(nameOf(timer)) || nameOf(timer).equals(REQUESTS))
                .map(timer -> new Reading(timer.getId(), measureOf(timer), takenSoFar.getOrDefault(timer.getId(), Taken.NOTHING), Taken.of(timer),
                        timer.max(TimeUnit.MILLISECONDS)))
                .toList();
    }

    private List<HealthSample> samplesOf(List<Reading> readings) {
        Map<String, HealthSample> byMeasure = new TreeMap<>();
        readings.stream().map(Reading::sample).filter(sample -> sample.count() > 0)
                .forEach(sample -> byMeasure.merge(sample.measure(), sample, HealthSample::plus));
        return Stream.concat(byMeasure.values().stream(), memoryInUse().stream()).toList();
    }

    private Optional<HealthSample> memoryInUse() {
        return meters.registry().getMeters().stream()
                .filter(meter -> nameOf(meter).equals(CoreMeters.MEMORY_USED) && meter instanceof Gauge)
                .map(Gauge.class::cast)
                .findFirst()
                .map(gauge -> new HealthSample(MEMORY_IN_USE, 1, gauge.value(), gauge.value()));
    }

    private static String measureOf(Timer timer) {
        String name = nameOf(timer);
        return name.equals(REQUESTS) ? requestMeasureOf(timer) : MEASURE_OF_TIMER.get(name);
    }

    private static String requestMeasureOf(Timer timer) {
        return "request " + timer.getId().getTag("method") + " " + timer.getId().getTag("uri");
    }

    private static String nameOf(Meter meter) {
        return meter.getId().getName();
    }

    private record Reading(Meter.Id timer, String measure, Taken before, Taken now, double maxMillis) {

        HealthSample sample() {
            return new HealthSample(measure, now.count() - before.count(), now.totalMillis() - before.totalMillis(), maxMillis);
        }
    }

    private record Taken(long count, double totalMillis) {

        static final Taken NOTHING = new Taken(0, 0);

        static Taken of(Timer timer) {
            return new Taken(timer.count(), timer.totalTime(TimeUnit.MILLISECONDS));
        }
    }
}
