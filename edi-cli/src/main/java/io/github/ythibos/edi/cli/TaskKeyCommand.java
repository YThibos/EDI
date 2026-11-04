package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "key", description = "Gives a task its work key after intake; on a task whose ticket decision is open, the key answers that decision with"
        + " the captain's words cited.")
final class TaskKeyCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(index = "0", paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Parameters(index = "1", paramLabel = "KEY", description = "The work key the task books its work on, such as EDI-7.")
    private String key;

    @Mixin
    private CaptainsWordsCited words;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/key", words.addedWhenCitedTo(new JsonBody().with("key", key), core));
    }
}
