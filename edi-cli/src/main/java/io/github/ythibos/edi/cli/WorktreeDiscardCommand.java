package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "discard", description = "Discards a task's isolated copy and its branch, unlanded work included, on the captain's word;"
        + " the task stays, and a later dispatch starts a fresh copy.")
final class WorktreeDiscardCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/discard-work", new JsonBody());
    }
}
