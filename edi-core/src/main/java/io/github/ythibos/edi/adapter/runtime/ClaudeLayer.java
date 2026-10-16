package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class ClaudeLayer implements RuntimeHintsRegistrar {

    static final String WORKER_SETTINGS = "claude/worker/settings.json";
    static final String WORKER_SYSTEM_PROMPT = "claude/worker/system-prompt.md";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern(WORKER_SETTINGS).registerPattern(WORKER_SYSTEM_PROMPT);
    }

    static String read(String resource) {
        try (InputStream content = ClaudeLayer.class.getClassLoader().getResourceAsStream(resource)) {
            if (content == null) {
                throw new IllegalStateException(resource + " is missing from the core's resources.");
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
