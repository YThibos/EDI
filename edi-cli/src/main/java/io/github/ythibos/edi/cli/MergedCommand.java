package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "merged", description = "Tells EDI the captain merged a task's work: EDI checks the forge, refreshes the clone and cleans up.")
final class MergedCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK|URL", description = "A task id, the D number of its decision, or the full URL of its merge request.")
    private String reference;

    @Override
    public Integer call() {
        return core.post("/api/merged", new JsonBody().with("reference", reference));
    }
}
