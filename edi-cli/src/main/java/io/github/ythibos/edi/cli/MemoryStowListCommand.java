package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "stow-list", description = "Lists what /stow should curate: undated entries, learnings due for a fresh check, duplicated headings.")
final class MemoryStowListCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.get("/api/memory/stow-list");
    }
}
