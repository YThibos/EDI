package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "release", description = "Releases a task from its deferral, its approval and its outage hold; only an answer releases a decision.")
final class ReleaseCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id.")
    private String task;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/release", new JsonBody());
    }
}
