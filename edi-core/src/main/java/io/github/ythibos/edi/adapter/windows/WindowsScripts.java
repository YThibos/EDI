package io.github.ythibos.edi.adapter.windows;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class WindowsScripts implements RuntimeHintsRegistrar {

    static final String TOAST = "windows/toast.ps1";
    static final String OUTLOOK_CALENDAR = "windows/outlook-calendar.ps1";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern(TOAST).registerPattern(OUTLOOK_CALENDAR);
    }

    static String read(String resource) {
        try (InputStream content = WindowsScripts.class.getClassLoader().getResourceAsStream(resource)) {
            if (content == null) {
                throw new IllegalStateException(resource + " is missing from the core's resources.");
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static List<String> encodedCommand(String script) {
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        return List.of("-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    static String encodedText(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
