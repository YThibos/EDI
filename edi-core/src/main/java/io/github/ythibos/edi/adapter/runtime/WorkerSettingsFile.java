package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

final class WorkerSettingsFile {

    private static final String FILE_NAME = "claude-settings.json";

    private WorkerSettingsFile() {
    }

    static Path writtenInto(Path stateDirectory) {
        try {
            Files.createDirectories(stateDirectory);
            Path settings = stateDirectory.resolve(FILE_NAME);
            Files.writeString(settings, ClaudeLayer.read(ClaudeLayer.WORKER_SETTINGS), StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(settings, PosixFilePermissions.fromString("rw-------"));
            return settings;
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
