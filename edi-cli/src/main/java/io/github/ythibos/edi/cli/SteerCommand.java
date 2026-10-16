package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "steer", description = "Sends a message to a task's worker and delivers it at its next stop or its idle prompt.")
final class SteerCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(index = "0", paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Parameters(index = "1", arity = "0..1", paramLabel = "-|FILE", description = "The message, from standard input (-, the default) or a file.")
    private String message = "-";

    @Option(names = "--resolves", paramLabel = "KEY", description = "The key of the decision or blocker the message answers.")
    private String resolves;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/steer",
                new JsonBody().with("text", core.contentOf(message)).with("resolves", resolves));
    }
}
