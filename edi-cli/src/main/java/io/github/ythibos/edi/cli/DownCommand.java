package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "down", description = "Stops EDI's core when no worker runs, or with --force-stop-workers on the captain's word; it never signals a worker"
        + " it did not record.")
final class DownCommand implements Callable<Integer> {

    private static final int STOPPED = 0;
    private static final int REFUSED = 1;

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Mixin
    private CoreExchange core;

    @Option(names = "--force-stop-workers", description = "Stops the running workers first, on the captain's word.")
    private boolean forceStopWorkers;

    @Override
    public Integer call() {
        if (core.post("/api/core/stop", new JsonBody().with("forceStopWorkers", forceStopWorkers)) != STOPPED) {
            return REFUSED;
        }
        try {
            new LocalCore(edi.home(), edi.coreExecutable()).awaitItsStop();
            return STOPPED;
        } catch (CommandFailure stillUp) {
            spec.commandLine().getErr().println(stillUp.getMessage());
            return REFUSED;
        }
    }
}
