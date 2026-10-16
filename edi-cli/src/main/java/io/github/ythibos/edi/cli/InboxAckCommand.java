package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "ack", description = "Acknowledges a message once the worker has acted on it.")
final class InboxAckCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "N", description = "The message's number.")
    private int number;

    @Override
    public Integer call() {
        return core.post("/api/inbox/" + number + "/ack", new JsonBody());
    }
}
