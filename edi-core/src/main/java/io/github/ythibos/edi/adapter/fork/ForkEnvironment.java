package io.github.ythibos.edi.adapter.fork;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.AsciiCharacters;

@Component
public class ForkEnvironment {

    private static final int MOST_DIGITS = 9;

    private final String pauseResurfaceSeconds;

    public ForkEnvironment(@Value("${FM_PAUSE_RESURFACE_SECS:}") String pauseResurfaceSeconds) {
        this.pauseResurfaceSeconds = pauseResurfaceSeconds.strip();
    }

    public Optional<Duration> pauseResurface() {
        return pauseResurfaceSeconds.length() <= MOST_DIGITS && AsciiCharacters.isDigits(pauseResurfaceSeconds)
                ? Optional.of(Duration.ofSeconds(Long.parseLong(pauseResurfaceSeconds)))
                : Optional.empty();
    }
}
