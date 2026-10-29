package io.github.ythibos.edi.application.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class RealPaths {

    private RealPaths() {
    }

    static Path of(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException absent) {
            return throughTheNearestExistingFolder(path.toAbsolutePath().normalize());
        }
    }

    private static Path throughTheNearestExistingFolder(Path absolute) {
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute));
        } catch (IOException unreadable) {
            return absolute;
        }
    }
}
