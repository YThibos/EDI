package io.github.ythibos.edi.domain.wallpaper;

import java.time.Duration;
import java.util.Objects;

public record Glimpse(Eyes eyes, Duration shown) {

    public Glimpse {
        Objects.requireNonNull(eyes, "A glimpse shows my eyes open, half closed or closed.");
        Objects.requireNonNull(shown, "A glimpse is shown for a while.");
        if (shown.isNegative() || shown.isZero()) {
            throw new IllegalArgumentException("A glimpse is shown for a positive duration.");
        }
    }
}
