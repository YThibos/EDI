package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "hold", description = "Holds a task for the captain until a date or until the captain's word.")
final class HoldCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Option(names = "--kind", required = true, paramLabel = "KIND", description = "deferral or approval; a decision holds its task through edi decision raise.")
    private String kind;

    @Option(names = "--reason", required = true, paramLabel = "REASON", description = "Why the task waits.")
    private String reason;

    @Option(names = "--until", paramLabel = "DATE", description = "The day the hold ends, such as 2026-11-01; without it, the hold lasts until the captain's word.")
    private String until;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/hold",
                new JsonBody().with("kind", kind).with("reason", reason).with("until", until));
    }
}
