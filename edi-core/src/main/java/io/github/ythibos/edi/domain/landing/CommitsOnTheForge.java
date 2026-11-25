package io.github.ythibos.edi.domain.landing;

import java.time.Duration;
import java.util.Optional;

public sealed interface CommitsOnTheForge {

    record Counted(String remote, int missing, Optional<Duration> sinceTheLastFetch) implements CommitsOnTheForge {

        private static final Duration STALE_AFTER = Duration.ofDays(1);

        public boolean areAllOnTheForge() {
            return missing == 0;
        }

        public boolean mayBeOutOfDate() {
            return sinceTheLastFetch.map(age -> age.compareTo(STALE_AFTER) > 0).orElse(true);
        }
    }

    record NotVerified(String reason) implements CommitsOnTheForge {
    }
}
