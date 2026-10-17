package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "promote", description = "Queues a ship from a scout on the captain's word, with the scout's projects, key, base and notes.")
final class TaskPromoteCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "SCOUT", description = "The scout's task id, or the D number of its decision.")
    private String scout;

    @Option(names = "--title", paramLabel = "TITLE", description = "The ship's title; the scout's title when left out.")
    private String title;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(scout) + "/promote", new JsonBody().with("title", title));
    }
}
