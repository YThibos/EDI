package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "health", description = "Reports whether EDI's core is running and healthy, and whether the primary's edi wait is live.")
final class HealthCommand implements Callable<Integer> {

    private static final int HEALTHY = 0;
    private static final int UNHEALTHY = 1;

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        Path portFile = CoreAddress.portFileIn(edi.home());
        Optional<CoreAddress> core = CoreAddress.recordedIn(portFile);
        if (core.isEmpty()) {
            return unhealthy("EDI core is not running: " + portFile + " records no port.");
        }
        return report(core.get(), new CoreClient().health(core.get()));
    }

    private Integer report(CoreAddress core, CoreHealth health) {
        return switch (health) {
            case UP -> healthy("EDI core is up on " + core + ".");
            case DOWN -> unhealthy("EDI core on " + core + " reports that it is not healthy.");
            case UNREACHABLE -> unhealthy("EDI core is not answering on " + core + ".");
        };
    }

    private Integer healthy(String message) {
        spec.commandLine().getOut().println(message);
        reportThePrimarysWait();
        return HEALTHY;
    }

    private Integer unhealthy(String message) {
        spec.commandLine().getErr().println(message);
        return UNHEALTHY;
    }

    private void reportThePrimarysWait() {
        try {
            CoreReply reply = new CoreClient().get(edi.core(), "/api/primary/wait", "text/plain");
            (reply.succeeded() ? spec.commandLine().getOut() : spec.commandLine().getErr()).println(reply.body().stripTrailing());
        } catch (CoreUnavailable unavailable) {
            spec.commandLine().getErr().println(unavailable.getMessage());
        }
    }
}
