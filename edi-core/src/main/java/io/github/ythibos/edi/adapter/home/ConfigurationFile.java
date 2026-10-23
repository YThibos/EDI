package io.github.ythibos.edi.adapter.home;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    static void replace(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Path written = Files.createTempFile(file.getParent(), file.getFileName() + "-", ".tmp");
            Files.writeString(written, content, StandardCharsets.UTF_8);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
