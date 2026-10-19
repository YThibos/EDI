package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "check", description = "Measures the memory loaded at session start against its budget and fails above it or on a duplicated"
        + " rule heading.")
final class MemoryCheckCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.get("/api/memory/check");
    }
}
