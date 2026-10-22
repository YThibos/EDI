package io.github.ythibos.edi.cli;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class RoundTripBudget {

    private static final Duration LIMIT_AT_P95 = Duration.ofMillis(50);
    private static final double PACE_MULTIPLE = 2.0;
    private static final Duration TIME_ALLOWED = Duration.ofSeconds(20);

    private final int roundTrips;

    RoundTripBudget(int roundTrips) {
        this.roundTrips = roundTrips;
    }

    Measurement measure(RoundTrip command, RoundTrip bareStartUp) throws Exception {
        List<Duration> taken = new ArrayList<>();
        List<Duration> bareStartUps = new ArrayList<>();
        long deadline = System.nanoTime() + TIME_ALLOWED.toNanos();
        while (taken.size() < roundTrips && System.nanoTime() < deadline) {
            bareStartUps.add(timed(bareStartUp));
            taken.add(timed(command));
        }
        return new Measurement(sorted(taken), sorted(bareStartUps));
    }

    private static Duration timed(RoundTrip roundTrip) throws Exception {
        long started = System.nanoTime();
        roundTrip.make();
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static List<Duration> sorted(List<Duration> durations) {
        return durations.stream().sorted().toList();
    }

    @FunctionalInterface
    interface RoundTrip {

        void make() throws Exception;
    }

    record Measurement(List<Duration> roundTrips, List<Duration> bareStartUps) {

        boolean metTheBudget() {
            return keptToTheLimit() || keptPaceWithTheMachine();
        }

        boolean keptToTheLimit() {
            return percentile(roundTrips, 95).compareTo(LIMIT_AT_P95) < 0;
        }

        boolean keptPaceWithTheMachine() {
            return paceRatio() < PACE_MULTIPLE;
        }

        String summary() {
            return "%d round trips: p50 %d ms, p95 %d ms, slowest %d ms; the p50 is %.2f times a bare start-up's p50 of %d ms; %s.".formatted(
                    roundTrips.size(), millisecondsAt(roundTrips, 50), millisecondsAt(roundTrips, 95), roundTrips.getLast().toMillis(), paceRatio(),
                    millisecondsAt(bareStartUps, 50), verdict());
        }

        private static Duration percentile(List<Duration> sorted, int percent) {
            return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percent / 100.0) - 1));
        }

        private double paceRatio() {
            return (double) percentile(roundTrips, 50).toNanos() / percentile(bareStartUps, 50).toNanos();
        }

        private static long millisecondsAt(List<Duration> sorted, int percent) {
            return percentile(sorted, percent).toMillis();
        }

        private String verdict() {
            if (keptToTheLimit()) {
                return "within %d ms at p95".formatted(LIMIT_AT_P95.toMillis());
            }
            return keptPaceWithTheMachine()
                    ? "over %d ms at p95 but within %.0f times a bare start-up at p50".formatted(LIMIT_AT_P95.toMillis(), PACE_MULTIPLE)
                    : "over %d ms at p95 and over %.0f times a bare start-up at p50".formatted(LIMIT_AT_P95.toMillis(), PACE_MULTIPLE);
        }
    }
}
