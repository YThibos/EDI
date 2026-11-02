package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

record StandardOutput(Path descriptor) {

    static final StandardOutput OF_THIS_PROCESS = new StandardOutput(Path.of("/proc/self/fd/1"));

    private static final Path NOWHERE = Path.of("/dev/null");

    Optional<String> whyNobodyReadsIt() {
        if (!Files.isDirectory(descriptor.getParent())) {
            return Optional.empty();
        }
        try {
            return Files.readSymbolicLink(descriptor).equals(NOWHERE) ? Optional.of("its standard output is /dev/null") : Optional.empty();
        } catch (NoSuchFileException closed) {
            return Optional.of("its standard output is closed");
        } catch (IOException | UnsupportedOperationException unreadable) {
            return Optional.empty();
        }
    }
}
