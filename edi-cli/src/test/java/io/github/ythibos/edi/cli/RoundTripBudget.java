package io.github.ythibos.edi.cli;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class RoundTripBudget {

    private final int roundTrips;
    private final int percentile;
    private final Duration limit;

    RoundTripBudget(int roundTrips, int percentile, Duration limit) {
        this.roundTrips = roundTrips;
        this.percentile = percentile;
        this.limit = limit;
    }

    Measurement measure(RoundTrip roundTrip) throws Exception {
        List<Duration> taken = new ArrayList<>();
        int overruns = 0;
        while (taken.size() < roundTrips && overruns <= allowedOverruns()) {
            Duration took = timed(roundTrip);
            taken.add(took);
            overruns += overruns(took) ? 1 : 0;
        }
        return new Measurement(taken.stream().sorted().toList(), overruns);
    }

    private int allowedOverruns() {
        return roundTrips - (int) Math.ceil(roundTrips * percentile / 100.0);
    }

    private static Duration timed(RoundTrip roundTrip) throws Exception {
        long started = System.nanoTime();
        roundTrip.make();
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private boolean overruns(Duration took) {
        return took.compareTo(limit) >= 0;
    }

    @FunctionalInterface
    interface RoundTrip {

        void make() throws Exception;
    }

    final class Measurement {

        private final List<Duration> sorted;
        private final int overruns;

        private Measurement(List<Duration> sorted, int overruns) {
            this.sorted = sorted;
            this.overruns = overruns;
        }

        boolean metTheBudget() {
            return overruns <= allowedOverruns();
        }

        boolean decidedEarly() {
            return sorted.size() < roundTrips;
        }

        String summary() {
            return metTheBudget()
                    ? "%d round trips: p50 %d ms, p95 %d ms, slowest %d ms.".formatted(sorted.size(), millisecondsAt(50), millisecondsAt(percentile),
                            sorted.getLast().toMillis())
                    : "%d of the first %d round trips took %d ms or more, where a p%d under %d ms over %d allows %d; p50 %d ms, slowest %d ms.".formatted(
                            overruns, sorted.size(), limit.toMillis(), percentile, limit.toMillis(), roundTrips, allowedOverruns(), millisecondsAt(50),
                            sorted.getLast().toMillis());
        }

        private long millisecondsAt(int percent) {
            return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percent / 100.0) - 1)).toMillis();
        }
    }
}
