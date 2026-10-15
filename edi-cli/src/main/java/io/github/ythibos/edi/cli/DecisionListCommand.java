package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "list", description = "Lists the decisions with their cards and answers.")
final class DecisionListCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Option(names = "--open", description = "Only the decisions still waiting for the captain.")
    private boolean open;

    @Override
    public Integer call() {
        return core.get(open ? "/api/decisions?open=true" : "/api/decisions");
    }
}
