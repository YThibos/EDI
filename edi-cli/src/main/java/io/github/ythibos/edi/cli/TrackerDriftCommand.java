package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "drift", description = "Lists the tickets whose state disagrees with the repository, and says which checks it could not make.")
final class TrackerDriftCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(index = "0", paramLabel = "PROJECT", description = "A registered project that books its work on JIRA.")
    private String project;

    @Override
    public Integer call() {
        return core.get("/api/tracker/drift?project=" + CoreExchange.encoded(project));
    }
}
