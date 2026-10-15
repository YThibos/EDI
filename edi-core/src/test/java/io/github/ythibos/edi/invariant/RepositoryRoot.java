package io.github.ythibos.edi.invariant;

import java.nio.file.Path;

final class RepositoryRoot {

    private RepositoryRoot() {
    }

    static Path path() {
        return Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    }
}
