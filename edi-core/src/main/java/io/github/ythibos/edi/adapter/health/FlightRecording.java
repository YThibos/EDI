package io.github.ythibos.edi.adapter.health;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

@Component
public class FlightRecording implements AutoCloseable {

    private static final String NAME = "edi-core";
    private static final String DUMPING_THREAD = "edi-flight-recording-dump";
    private static final int DUMPS_KEPT = 5;
    private static final DateTimeFormatter DUMP_NAME = DateTimeFormatter.ofPattern("'core-'yyyyMMdd-HHmmss'.jfr'").withZone(ZoneOffset.UTC);
    private static final String SLOWER_THAN = "20 ms";
    private static final String ENABLED = "#enabled";
    private static final Map<String, String> EVENTS_WITHOUT_PERSONAL_CONTENT = Map.ofEntries(
            Map.entry("jdk.ExecutionSample#enabled", "true"),
            Map.entry("jdk.ExecutionSample#period", "20 ms"),
            Map.entry("jdk.GarbageCollection#enabled", "true"),
            Map.entry("jdk.GarbageCollection#threshold", "0 ms"),
            Map.entry("jdk.GCHeapSummary#enabled", "true"),
            Map.entry("jdk.CPULoad#enabled", "true"),
            Map.entry("jdk.CPULoad#period", "1 s"),
            Map.entry("jdk.ThreadCPULoad#enabled", "true"),
            Map.entry("jdk.ThreadCPULoad#period", "10 s"),
            Map.entry("jdk.JavaMonitorEnter#enabled", "true"),
            Map.entry("jdk.JavaMonitorEnter#threshold", SLOWER_THAN),
            Map.entry("jdk.ThreadPark#enabled", "true"),
            Map.entry("jdk.ThreadPark#threshold", SLOWER_THAN),
            Map.entry("jdk.VirtualThreadPinned#enabled", "true"),
            Map.entry("jdk.VirtualThreadPinned#threshold", SLOWER_THAN),
            Map.entry("jdk.ObjectAllocationSample#enabled", "true"),
            Map.entry("jdk.ObjectAllocationSample#throttle", "150/s"));

    private final Optional<Recording> recording;
    private final Path dumps;
    private final Clock clock;
    private final DataSize maxSize;

    public FlightRecording(EdiHome home, Clock clock, @Value("${edi.health.recording.max-size:64MB}") DataSize maxSize,
            @Value("${edi.health.recording.max-age:P1D}") Duration maxAge) {
        this.dumps = home.root().resolve("data").resolve("recordings");
        this.clock = clock;
        this.maxSize = maxSize;
        this.recording = FlightRecorder.isAvailable() ? Optional.of(started(maxSize, maxAge)) : Optional.empty();
    }

    public RecordingState state() {
        return recording.map(running -> new RecordingState(true, running.getSize(), maxSize.toBytes()))
                .orElseGet(() -> new RecordingState(false, 0, maxSize.toBytes()));
    }

    public Path dump() {
        Recording running = recording.orElseThrow(() -> new Refusal(Rule.FLIGHT_RECORDING,
                "This core runs without a flight recorder, so there is no recording to write out."));
        Path dumped = dumps.resolve(DUMP_NAME.format(clock.now()));
        try {
            Files.createDirectories(dumps);
            dumpedOnAPlatformThread(running, dumped);
            keepTheLatestDumps();
            return dumped;
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    @Override
    public void close() {
        recording.ifPresent(Recording::close);
    }

    private static Recording started(DataSize maxSize, Duration maxAge) {
        Recording recording = new Recording(onlyTheEventsWithoutPersonalContent());
        recording.setName(NAME);
        recording.setToDisk(true);
        recording.setMaxSize(maxSize.toBytes());
        recording.setMaxAge(maxAge);
        recording.start();
        return recording;
    }

    private static void dumpedOnAPlatformThread(Recording running, Path dumped) throws IOException {
        FutureTask<Path> written = new FutureTask<>(() -> {
            running.dump(dumped);
            return dumped;
        });
        Thread.ofPlatform().name(DUMPING_THREAD).start(written);
        try {
            written.get();
        } catch (ExecutionException failed) {
            throw failed.getCause() instanceof IOException unwritable ? unwritable : new IOException(failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("The flight recording was not written out: its writing was interrupted.", interrupted);
        }
    }

    private static Map<String, String> onlyTheEventsWithoutPersonalContent() {
        Map<String, String> settings = new HashMap<>(EVENTS_WITHOUT_PERSONAL_CONTENT);
        FlightRecorder.getFlightRecorder().getEventTypes().stream()
                .map(EventType::getName)
                .filter(name -> !settings.containsKey(name + ENABLED))
                .forEach(name -> settings.put(name + ENABLED, "false"));
        return settings;
    }

    private void keepTheLatestDumps() throws IOException {
        List<Path> older;
        try (Stream<Path> dumped = Files.list(dumps)) {
            older = dumped.filter(file -> file.getFileName().toString().endsWith(".jfr")).sorted(Comparator.reverseOrder()).skip(DUMPS_KEPT).toList();
        }
        for (Path dump : older) {
            Files.delete(dump);
        }
    }

    public record RecordingState(boolean running, long bytes, long mostBytes) {
    }
}
