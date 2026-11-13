package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class LocalCore {

    static final Duration STARTING_PATIENCE = Duration.ofSeconds(60);
    static final Duration STOPPING_PATIENCE = Duration.ofSeconds(90);
    private static final String FOR_ITS_OWNER_ALONE = "umask 077 && exec \"$0\"";
    private static final Duration BETWEEN_LOOKS = Duration.ofMillis(250);

    private final Path home;
    private final String executable;
    private final Duration stoppingPatience;
    private final CorePidFile pidFile;
    private final CoreClient client = new CoreClient();

    LocalCore(Path home, String executable) {
        this(home, executable, STOPPING_PATIENCE);
    }

    LocalCore(Path home, String executable, Duration stoppingPatience) {
        this.home = home;
        this.executable = executable;
        this.stoppingPatience = stoppingPatience;
        this.pidFile = CorePidFile.in(home);
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
        LocalPrograms.startDetached(List.of("setsid", "-f", "sh", "-c", FOR_ITS_OWNER_ALONE, executable), home, log, Map.of("EDI_HOME", home.toString()));
        if (!comesTrue(this::answers, STARTING_PATIENCE)) {
            throw new CommandFailure("EDI core did not answer within " + STARTING_PATIENCE.toSeconds() + " seconds; " + log + " says why.");
        }
    }

    void awaitItsStop() {
        if (!comesTrue(this::hasReleasedTheHome, stoppingPatience)) {
            throw new CommandFailure("EDI core, process " + pidFile.recordedProcess() + ", still holds " + pidFile + " after "
                    + stoppingPatience.toSeconds() + " seconds, so it has not stopped.");
        }
    }

    private static boolean comesTrue(BooleanSupplier condition, Duration patience) {
        long deadline = System.nanoTime() + patience.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            pause();
        }
        return condition.getAsBoolean();
    }

    private boolean answers() {
        return health() == CoreHealth.UP;
    }

    private boolean hasReleasedTheHome() {
        return !pidFile.isHeld();
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
