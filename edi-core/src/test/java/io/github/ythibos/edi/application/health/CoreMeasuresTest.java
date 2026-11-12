package io.github.ythibos.edi.application.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.health.CoreMeters;
import io.github.ythibos.edi.adapter.store.HealthSamples;
import io.github.ythibos.edi.adapter.store.HealthSamples.HealthSample;
import io.github.ythibos.edi.testing.TestHome;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CoreMeasuresTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private CoreMeters meters;
    private CoreMeasures measures;

    @BeforeEach
    void measureAHome() throws IOException {
        home = TestHome.in(directory);
        meters = home.meters();
        measures = new CoreMeasures(meters);
    }

    @Test
    void takesWhatEachMeasureCountedSinceItWasLastTakenAndWhatMemoryIsInUse() {
        meters.wakePrinted(Duration.ofSeconds(3));
        meters.wakePrinted(Duration.ofSeconds(1));
        Timer.builder("http.server.requests").tags("method", "GET", "uri", "/api/rules", "status", "200").register(meters.registry())
                .record(Duration.ofMillis(40));
        Timer.builder("http.server.requests").tags("method", "GET", "uri", "/api/rules", "status", "400").register(meters.registry())
                .record(Duration.ofMillis(10));

        assertThat(measures.pending()).extracting(HealthSample::measure).containsExactly("request GET /api/rules", "wake raised to printed",
                CoreMeasures.MEMORY_IN_USE);
        assertThat(measures.taken()).filteredOn(sample -> !sample.measure().equals(CoreMeasures.MEMORY_IN_USE))
                .extracting(HealthSample::measure, HealthSample::count, HealthSample::total, HealthSample::max)
                .containsExactly(tuple("request GET /api/rules", 2L, 50.0, 40.0), tuple("wake raised to printed", 2L, 4000.0, 3000.0));
        assertThat(measures.taken()).extracting(HealthSample::measure).containsExactly(CoreMeasures.MEMORY_IN_USE);
        assertThat(measures.pending()).singleElement().satisfies(memory -> assertThat(memory.total()).isPositive());
    }

    @Test
    void takesNoMaximumBelowTheMeanOnceTheTimersOwnMaximumHasDecayed() {
        MockClock clock = new MockClock();
        CoreMeters decaying = new CoreMeters(new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock));
        decaying.workerLaunch(() -> {
            clock.add(Duration.ofSeconds(40));
            return "launched";
        });
        clock.add(Duration.ofMinutes(10));

        assertThat(new CoreMeasures(decaying).taken()).filteredOn(sample -> sample.measure().equals("worker launch"))
                .extracting(HealthSample::count, HealthSample::total, HealthSample::max)
                .containsExactly(tuple(1L, 40_000.0, 40_000.0));
    }

    @Test
    void recordsTheTakenMeasuresAndSumsThemOverAPeriod() {
        HealthSamples samples = new HealthSamples(home.jdbc());
        RecordCoreHealth record = new RecordCoreHealth(measures, samples, Duration.ofDays(35), home.clock(), home.transaction());
        meters.supervisionRound(() -> home.clock().advance(Duration.ZERO));
        record.record();
        home.clock().advance(Duration.ofMinutes(10));
        meters.supervisionRound(() -> home.clock().advance(Duration.ZERO));
        record.record();

        assertThat(samples.summedSince(TestHome.START)).extracting(HealthSample::measure, HealthSample::count)
                .containsExactly(tuple(CoreMeasures.MEMORY_IN_USE, 2L), tuple("supervision round", 2L));
        assertThat(samples.summedSince(TestHome.START.plusSeconds(1))).extracting(HealthSample::measure, HealthSample::count)
                .containsExactly(tuple(CoreMeasures.MEMORY_IN_USE, 1L), tuple("supervision round", 1L));
    }

    @Test
    void forgetsTheSamplesOlderThanItKeeps() {
        HealthSamples samples = new HealthSamples(home.jdbc());
        RecordCoreHealth record = new RecordCoreHealth(measures, samples, Duration.ofDays(35), home.clock(), home.transaction());
        record.record();
        home.clock().advance(Duration.ofDays(36));

        record.record();

        assertThat(samples.summedSince(TestHome.START)).extracting(HealthSample::count).containsExactly(1L);
    }
}
