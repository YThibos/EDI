package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "backlog", description = "Lists the queued tasks in order, with what holds or blocks each.")
final class BacklogCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Option(names = "--ready", description = "Only the tasks nothing holds or blocks.")
    private boolean ready;

    @Override
    public Integer call() {
        return core.get(ready ? "/api/backlog?ready=true" : "/api/backlog");
    }
}
