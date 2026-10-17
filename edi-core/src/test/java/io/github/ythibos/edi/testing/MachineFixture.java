package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public enum MachineFixture {
    LAPTOP,
    DESKTOP;

    public static final String PROPERTY = "edi.test.machine";

    public static MachineFixture current() {
        String named = System.getProperty(PROPERTY, LAPTOP.word());
        return Arrays.stream(values())
                .filter(machine -> machine.word().equals(named))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(PROPERTY + " names laptop or desktop, not " + named + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String profile() {
        try (InputStream content = MachineFixture.class.getClassLoader().getResourceAsStream("machine-profiles/" + word() + ".yaml")) {
            if (content == null) {
                throw new IllegalStateException("The " + word() + " fixture profile is missing from the test resources.");
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
