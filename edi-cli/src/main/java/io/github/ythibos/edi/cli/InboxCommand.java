package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "inbox", description = "Shows a worker the messages waiting for it.", subcommands = InboxAckCommand.class)
final class InboxCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.get("/api/inbox");
    }
}
