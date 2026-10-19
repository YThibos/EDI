package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "drop", description = "Drops a task on the captain's word, retiring its isolated copy and branch once their work is landed.")
final class TaskDropCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Option(names = "--discard-work", description = "Also discard work that has not landed, on the captain's word.")
    private boolean discardWork;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/drop", new JsonBody().with("discardWork", discardWork));
    }
}
