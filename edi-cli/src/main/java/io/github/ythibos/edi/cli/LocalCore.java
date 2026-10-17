package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class LocalCore {

    static final Duration STARTING_PATIENCE = Duration.ofSeconds(60);
    static final Duration STOPPING_PATIENCE = Duration.ofSeconds(30);
    private static final Duration BETWEEN_LOOKS = Duration.ofMillis(250);

    private final Path home;
    private final String executable;
    private final CoreClient client = new CoreClient();

    LocalCore(Path home, String executable) {
        this.home = home;
        this.executable = executable;
    }

    CoreHealth health() {
        return CoreAddress.recordedIn(CoreAddress.portFileIn(home)).map(client::health).orElse(CoreHealth.UNREACHABLE);
    }

    void start() {
        Path log = home.resolve("state").resolve("logs").resolve("core.log");
        try {
            Files.createDirectories(log.getParent());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
        LocalPrograms.startDetached(List.of("setsid", "-f", executable), log, Map.of("EDI_HOME", home.toString()));
        if (!becomes(CoreHealth.UP, STARTING_PATIENCE)) {
            throw new CommandFailure("EDI core did not answer within " + STARTING_PATIENCE.toSeconds() + " seconds; " + log + " says why.");
        }
    }

    void awaitItsStop() {
        if (!becomes(CoreHealth.UNREACHABLE, STOPPING_PATIENCE)) {
            throw new CommandFailure("EDI core still answers " + STOPPING_PATIENCE.toSeconds() + " seconds after it was asked to stop.");
        }
    }

    private boolean becomes(CoreHealth wanted, Duration patience) {
        long deadline = System.nanoTime() + patience.toNanos();
        while (System.nanoTime() < deadline) {
            if (health() == wanted) {
                return true;
            }
            pause();
        }
        return health() == wanted;
    }

    private static void pause() {
        try {
            Thread.sleep(BETWEEN_LOOKS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CommandFailure("Waiting for EDI core was interrupted.");
        }
    }
}
