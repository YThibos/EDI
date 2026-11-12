package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "list", description = "Lists the open sparks, oldest first, with their number, time, author and focus, for the gardening to sort.")
final class SparkListCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.get("/api/sparks");
    }
}
