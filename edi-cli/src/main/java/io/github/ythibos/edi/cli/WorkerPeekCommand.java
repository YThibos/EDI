package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "peek", description = "Shows the last lines of a task's worker pane and what EDI reads in them.")
final class WorkerPeekCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Override
    public Integer call() {
        return core.get("/api/tasks/" + CoreExchange.encoded(task) + "/peek");
    }
}
