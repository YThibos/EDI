package io.github.ythibos.edi.cli;

import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "up", description = "Starts EDI's core when it is not running, then opens the primary session in tmux with its anchor, memory,"
        + " hooks and launch key, or resumes an earlier one with --resume.")
final class UpCommand implements Callable<Integer> {

    private static final int REFUSED = 1;
    private static final String TEXT = "text/plain";

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Option(names = "--core-only", description = "Starts only the core.")
    private boolean coreOnly;

    @Option(names = "--resume", paramLabel = "SESSION", description = "Resumes the primary session with this Claude Code session id, with the launch"
            + " key a primary session needs; a session opened before the launch key existed comes back this way.")
    private UUID resumedSession;

    @Option(names = "--no-board", description = "Opens the primary session without opening the console's sitrep page, whatever console.openSitrepAtSessionStart says.")
    private boolean noBoard;

    @Override
    public Integer call() {
        try {
            startTheCoreUnlessItRuns();
            return coreOnly ? 0 : primaryWindow().open(launchOfThePrimary());
        } catch (CommandFailure | CoreUnavailable failed) {
            spec.commandLine().getErr().println(failed.getMessage());
            return REFUSED;
        }
    }

    private void startTheCoreUnlessItRuns() {
        LocalCore core = new LocalCore(edi.home(), edi.coreExecutable());
        PrintWriter out = spec.commandLine().getOut();
        if (core.health() == CoreHealth.UP) {
            out.println("EDI core is already up.");
        } else {
            core.start();
            out.println("EDI core is up.");
        }
        out.flush();
    }

    PrimaryWindow primaryWindow() {
        return new PrimaryWindow(edi.environment(), edi.home(), UpLaunch.asked(noBoard));
    }

    private PrimaryLaunch launchOfThePrimary() {
        CoreReply reply = new CoreClient().post(edi.core(), "/api/primary/launch", "{}", TEXT);
        if (!reply.succeeded()) {
            throw new CommandFailure(reply.body().strip());
        }
        return PrimaryLaunch.read(reply.body()).resuming(resumedSession);
    }
}
