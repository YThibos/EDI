package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "Shows a task's worker from the store: its state, its last status in full, its open question, its steers and its pipeline.")
final class WorkerShowCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Override
    public Integer call() {
        return core.get("/api/tasks/" + CoreExchange.encoded(task) + "/worker");
    }
}
