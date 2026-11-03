package io.github.ythibos.edi.domain.wallpaper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class BlinkTest {

    @Test
    void holdsTheEyesOpenUntilTheBlinkThatEndsEachLoop() {
        Blink blink = new Blink(Duration.ofSeconds(20));

        assertThat(blink.loop()).extracting(Glimpse::eyes).containsExactly(Eyes.OPEN, Eyes.HALF_CLOSED, Eyes.CLOSED, Eyes.HALF_CLOSED);
        assertThat(blink.loop().getFirst().shown()).isEqualTo(Duration.ofMillis(19_830));
        assertThat(blink.loop().stream().map(Glimpse::shown).reduce(Duration.ZERO, Duration::plus)).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void blinksEveryFifteenSecondsUnlessTheCaptainSaysOtherwise() {
        assertThat(Blink.SETTLED.every()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void blinksAtLeastEveryTenMinutesAndAtMostEverySecond() {
        assertThat(new Blink(Blink.SHORTEST).loop().getFirst().shown()).isEqualTo(Duration.ofMillis(830));
        assertThat(new Blink(Blink.LONGEST).every()).isEqualTo(Duration.ofMinutes(10));
        assertThatThrownBy(() -> new Blink(Duration.ofMillis(999))).hasMessage("wallpaper.blinkEvery is between PT1S and PT10M, not PT0.999S.");
        assertThatThrownBy(() -> new Blink(Duration.ofMinutes(11))).hasMessage("wallpaper.blinkEvery is between PT1S and PT10M, not PT11M.");
    }
}
