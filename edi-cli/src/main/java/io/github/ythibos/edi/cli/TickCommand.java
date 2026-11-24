package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "tick", description = "Ticks an entry the captain has dealt with off the sitrep page's list, on the captain's cited word.")
final class TickCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "CALL", description = "The entry as the sitrep names it: a D number, board:<number> or review:<task>.")
    private String call;

    @Mixin
    private CaptainsWordsCited words;

    @Override
    public Integer call() {
        return core.post("/api/calls/" + CoreExchange.encoded(call) + "/tick", words.addedTo(new JsonBody(), core));
    }
}
