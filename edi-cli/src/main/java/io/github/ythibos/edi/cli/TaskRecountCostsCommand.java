package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "recount-costs", description = "Recounts the pipeline's spend of every task cost counted since a day, from the pipeline's own record,"
        + " and records each cost it changes.")
final class TaskRecountCostsCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Option(names = "--since", required = true, paramLabel = "DATE", description = "The first day whose task costs are recounted, such as 2026-11-02.")
    private String since;

    @Override
    public Integer call() {
        return core.post("/api/tasks/recount-costs", new JsonBody().with("since", since));
    }
}
