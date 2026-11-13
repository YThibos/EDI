package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "add", description = "Enters a plan as JSON {key, title, project, report, decision, capacity, effort,"
        + " slices: [{number, title, category, key, body, wait}]}, from standard input (-) or a file; a slice's wait is \"after N merged\""
        + " (by default on the slice before), \"after N landed\" or \"none\".")
final class PlanAddCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Option(names = "--from", required = true, paramLabel = "-|FILE", description = "The plan: - for standard input, or a file.")
    private String plan;

    @Override
    public Integer call() {
        return core.post("/api/plans", core.contentOf(plan));
    }
}
