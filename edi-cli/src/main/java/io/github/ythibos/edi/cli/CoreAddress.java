package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

record CoreAddress(int port) {

    private static final String LOOPBACK = "127.0.0.1";
    private static final Pattern RECORDED_PORT = Pattern.compile("\\d{1,5}");

    static Path portFileIn(Path home) {
        return home.resolve("state").resolve("core.port");
    }

    static Optional<CoreAddress> recordedIn(Path portFile) {
        try {
            return portIn(Files.readString(portFile).strip()).map(CoreAddress::new);
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Optional<Integer> portIn(String recorded) {
        return RECORDED_PORT.matcher(recorded).matches() ? Optional.of(Integer.parseInt(recorded)) : Optional.empty();
    }

    URI endpoint(String path) {
        return URI.create("http://" + this + path);
    }

    @Override
    public String toString() {
        return LOOPBACK + ":" + port;
    }
}
