package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "fold", description = "Folds a spark into an existing backlog item that already covers the idea.")
final class SparkFoldCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(index = "0", paramLabel = "SPARK", description = "The spark's number, as edi spark list shows it.")
    private int spark;

    @Parameters(index = "1", paramLabel = "KEY", description = "The key of the existing backlog item, such as EDI-120.")
    private String key;

    @Override
    public Integer call() {
        return core.post("/api/sparks/" + spark + "/fold", new JsonBody().with("key", key));
    }
}
