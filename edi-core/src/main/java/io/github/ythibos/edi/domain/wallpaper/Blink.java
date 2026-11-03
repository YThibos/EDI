package io.github.ythibos.edi.domain.wallpaper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record Blink(Duration every) {

    public static final Duration EVERY = Duration.ofSeconds(15);
    public static final Duration SHORTEST = Duration.ofSeconds(1);
    public static final Duration LONGEST = Duration.ofMinutes(10);
    public static final Blink SETTLED = new Blink(EVERY);

    private static final List<Glimpse> CLOSING_AND_OPENING = List.of(
            new Glimpse(Eyes.HALF_CLOSED, Duration.ofMillis(40)),
            new Glimpse(Eyes.CLOSED, Duration.ofMillis(80)),
            new Glimpse(Eyes.HALF_CLOSED, Duration.ofMillis(50)));

    public Blink {
        Objects.requireNonNull(every, "wallpaper.blinkEvery says how often I blink.");
        if (every.compareTo(SHORTEST) < 0 || every.compareTo(LONGEST) > 0) {
            throw new IllegalArgumentException("wallpaper.blinkEvery is between " + SHORTEST + " and " + LONGEST + ", not " + every + ".");
        }
    }

    public List<Glimpse> loop() {
        return Stream.concat(Stream.of(new Glimpse(Eyes.OPEN, every.minus(blinking()))), CLOSING_AND_OPENING.stream()).toList();
    }

    private static Duration blinking() {
        return CLOSING_AND_OPENING.stream().map(Glimpse::shown).reduce(Duration.ZERO, Duration::plus);
    }
}
