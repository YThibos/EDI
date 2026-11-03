package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "messages", description = "Lists the captain's messages stored in this session, numbered: what they typed and how they"
        + " answered dialogs.")
final class CaptainMessagesCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.get("/api/captain/messages");
    }
}
