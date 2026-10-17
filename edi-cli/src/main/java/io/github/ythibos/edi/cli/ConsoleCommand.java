package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "console", description = "Opens the EDI console in the captain's browser, signed in through a link that works once.")
final class ConsoleCommand implements Callable<Integer> {

    static final String OPEN = "/api/console/open";

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.post(OPEN, new JsonBody());
    }
}
