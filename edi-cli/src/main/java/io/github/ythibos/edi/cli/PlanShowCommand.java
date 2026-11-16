package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "Shows a plan's slices with their stages and waits, and which of them are eligible to start.")
final class PlanShowCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "PLAN", description = "The plan's umbrella work key, such as EDI-52.")
    private String plan;

    @Override
    public Integer call() {
        return core.get("/api/plans/" + CoreExchange.encoded(plan));
    }
}
