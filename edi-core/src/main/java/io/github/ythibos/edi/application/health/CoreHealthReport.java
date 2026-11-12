package io.github.ythibos.edi.application.health;

import java.time.Instant;
import java.util.List;

public record CoreHealthReport(Instant since, List<Timing> timings, Memory memory, Recording recording) {

    public CoreHealthReport {
        timings = List.copyOf(timings);
    }

    public record Timing(String measure, long count, double meanMillis, double maxMillis) {
    }

    public record Memory(double meanBytes, double peakBytes) {
    }

    public record Recording(boolean running, long bytes, long mostBytes) {
    }
}
