package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "health", description = "Reports whether EDI's core is running and healthy, and whether the primary's edi wait is live;"
        + " --metrics adds the core's own health over a period, and --dump-recording writes out its flight recording for profiling.")
final class HealthCommand implements Callable<Integer> {

    private static final int HEALTHY = 0;
    private static final int UNHEALTHY = 1;

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Option(names = "--metrics", description = "Also print the core's own health: request, wake, store, supervision and launch times, and memory.")
    private boolean metrics;

    @Option(names = "--days", paramLabel = "N", defaultValue = "7", description = "The period --metrics covers: the last N days, 7 unless given.")
    private int days;

    @Option(names = "--dump-recording", description = "Also write out the core's flight recording into the EDI home and print where it is.")
    private boolean dumpRecording;

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
        if (reported("/api/primary/wait", "whether the primary's edi wait is live")) {
            reported("/api/workers/questions", "which worker questions wait on an answer");
        }
        boolean measured = !metrics || reported("/api/telemetry?days=" + days, "the core's own health");
        boolean dumped = !dumpRecording || written("/api/telemetry/recordings", "for the core's flight recording");
        return measured && dumped ? HEALTHY : UNHEALTHY;
    }

    private Integer unhealthy(String message) {
        spec.commandLine().getErr().println(message);
        return UNHEALTHY;
    }

    private boolean reported(String path, String what) {
        try {
            return printed(new CoreClient().get(edi.core(), path, "text/plain"), what);
        } catch (CoreUnavailable unavailable) {
            return cannotAsk(what, unavailable.getMessage());
        }
    }

    private boolean written(String path, String what) {
        try {
            return printed(new CoreClient().post(edi.core(), path, "{}", "text/plain"), what);
        } catch (CoreUnavailable unavailable) {
            return cannotAsk(what, unavailable.getMessage());
        }
    }

    private boolean printed(CoreReply reply, String what) {
        if (reply.succeeded()) {
            spec.commandLine().getOut().println(reply.body().stripTrailing());
            return true;
        }
        return cannotAsk(what, reply.body().stripTrailing());
    }

    private boolean cannotAsk(String what, String reason) {
        spec.commandLine().getErr().println("Cannot ask " + what + ": " + reason);
        return false;
    }
}
