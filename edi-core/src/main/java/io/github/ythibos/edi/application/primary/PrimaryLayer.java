package io.github.ythibos.edi.application.primary;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class PrimaryLayer implements RuntimeHintsRegistrar {

    static final String ANCHOR = "claude/primary/CLAUDE.md";
    static final String SETTINGS = "claude/primary/settings.json";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern(ANCHOR).registerPattern(SETTINGS);
    }

    static String read(String resource) {
        try (InputStream content = PrimaryLayer.class.getClassLoader().getResourceAsStream(resource)) {
            if (content == null) {
                throw new IllegalStateException(resource + " is missing from the core's resources.");
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
