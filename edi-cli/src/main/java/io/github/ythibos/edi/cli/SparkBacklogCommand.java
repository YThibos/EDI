package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "backlog", description = "Sorts a spark into the backlog: it became the backlog item with the key given.")
final class SparkBacklogCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(index = "0", paramLabel = "SPARK", description = "The spark's number, as edi spark list shows it.")
    private int spark;

    @Parameters(index = "1", paramLabel = "KEY", description = "The key of the backlog item the spark became, such as EDI-190.")
    private String key;

    @Override
    public Integer call() {
        return core.post("/api/sparks/" + spark + "/backlog", new JsonBody().with("key", key));
    }
}
