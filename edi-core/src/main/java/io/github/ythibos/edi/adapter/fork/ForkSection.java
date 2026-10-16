package io.github.ythibos.edi.adapter.fork;

import java.util.Locale;
import java.util.Optional;

public enum ForkSection {
    IN_FLIGHT,
    QUEUED,
    DONE;

    static Optional<ForkSection> titled(String title) {
        String heading = title.toLowerCase(Locale.ROOT);
        if (heading.equals("in flight")) {
            return Optional.of(IN_FLIGHT);
        }
        if (heading.equals("queued")) {
            return Optional.of(QUEUED);
        }
        return heading.startsWith("done") ? Optional.of(DONE) : Optional.empty();
    }
}
