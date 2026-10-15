package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "note", description = "Adds the note on standard input to a task.")
final class TaskNoteCommand implements Callable<Integer> {

    private static final String STANDARD_INPUT = "-";

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/notes", new JsonBody().with("note", core.contentOf(STANDARD_INPUT)));
    }
}
