package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "list", description = "Lists the workers serving tasks, with what each runs on and its last status.")
final class WorkerListCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.get("/api/workers");
    }
}
