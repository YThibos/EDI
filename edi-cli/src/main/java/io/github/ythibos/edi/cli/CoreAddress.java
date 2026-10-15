package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

record CoreAddress(int port) {

    private static final String LOOPBACK = "127.0.0.1";

    static Path portFileIn(Path home) {
        return home.resolve("state").resolve("core.port");
    }

    static Optional<CoreAddress> recordedIn(Path portFile) {
        try {
            return Optional.of(new CoreAddress(Integer.parseInt(Files.readString(portFile).strip())));
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    URI endpoint(String path) {
        return URI.create("http://" + this + path);
    }

    @Override
    public String toString() {
        return LOOPBACK + ":" + port;
    }
}
