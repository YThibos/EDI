package io.github.ythibos.edi.cli;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "up", description = "Starts EDI's core when it is not running, then opens the primary session in tmux with its anchor, memory"
        + " and hooks.")
final class UpCommand implements Callable<Integer> {

    private static final int REFUSED = 1;
    private static final String TEXT = "text/plain";

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Option(names = "--core-only", description = "Starts only the core.")
    private boolean coreOnly;

    @Override
    public Integer call() {
        try {
            startTheCoreUnlessItRuns();
            return coreOnly ? 0 : new PrimaryWindow(edi.environment()).open(launchOfThePrimary());
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

    private PrimaryLaunch launchOfThePrimary() {
        CoreReply reply = new CoreClient().post(edi.core(), "/api/primary/launch", "{}", TEXT);
        if (!reply.succeeded()) {
            throw new CommandFailure(reply.body().strip());
        }
        return PrimaryLaunch.read(reply.body());
    }
}
