package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "walk", description = "Moves a ticket to a state along the shortest path of the project's workflow, stopping with the path taken when a step fails.")
final class TrackerWalkCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(index = "0", paramLabel = "KEY", description = "The ticket's work key, such as EXAMPLE-7.")
    private String key;

    @Parameters(index = "1", paramLabel = "STATE", description = "The state the ticket is to reach, such as \"Ready For Prd\".")
    private String state;

    @Override
    public Integer call() {
        return core.post("/api/tracker/walk", new JsonBody().with("key", key).with("state", state));
    }
}
