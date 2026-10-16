package io.github.ythibos.edi.adapter.home;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

final class ConfigurationFile {

    private ConfigurationFile() {
    }

    static Optional<String> contentOf(Path file) {
        try {
            String content = Files.readString(file);
            return content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
