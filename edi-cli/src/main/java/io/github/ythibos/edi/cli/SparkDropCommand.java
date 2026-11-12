package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "drop", description = "Drops a spark with the reason it goes no further.")
final class SparkDropCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "SPARK", description = "The spark's number, as edi spark list shows it.")
    private int spark;

    @Option(names = "--reason", required = true, paramLabel = "REASON", description = "Why the idea goes no further.")
    private String reason;

    @Override
    public Integer call() {
        return core.post("/api/sparks/" + spark + "/drop", new JsonBody().with("reason", reason));
    }
}
