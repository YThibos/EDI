package io.github.ythibos.edi.adapter.runtime;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

final class EdiOnThePath {

    private static final String EDI = "edi";

    private EdiOnThePath() {
    }

    static Optional<Path> in(String searchPath) {
        if (searchPath == null) {
            return Optional.empty();
        }
        return Arrays.stream(searchPath.split(File.pathSeparator))
                .filter(directory -> !directory.isBlank())
                .map(directory -> Path.of(directory).resolve(EDI).toAbsolutePath())
                .filter(candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate))
                .findFirst();
    }
}
