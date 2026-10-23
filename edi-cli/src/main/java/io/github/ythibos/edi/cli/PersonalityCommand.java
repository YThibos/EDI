package io.github.ythibos.edi.cli;

import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "personality", description = "Shows the personality EDI speaks with, or changes it: a preset, or scales with their levels from 0 to 10;"
        + " every change is kept.")
final class PersonalityCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "CHANGE", arity = "0..*",
            description = "A preset (balanced, plain or demo), or scales with their levels, such as humour 6 formality 8; shows the personality when left out.")
    private List<String> change;

    @Override
    public Integer call() {
        if (change == null || change.isEmpty()) {
            return core.get("/api/personality");
        }
        return core.post("/api/personality", new JsonBody().with("change", change));
    }
}
