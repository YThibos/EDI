package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "publish-check", description = "Checks the outgoing commits of this repository for secrets and personal material before a push.")
final class PublishCheckCommand implements Callable<Integer> {

    private static final String UPSTREAM_TO_HEAD = "@{upstream}..HEAD";

    @Mixin
    private CoreExchange core;

    @Option(names = "--range", paramLabel = "A..B", description = "The outgoing commits; by default those between the branch's upstream and HEAD.")
    private String range = UPSTREAM_TO_HEAD;

    @Override
    public Integer call() {
        return core.post("/api/publish-checks",
                new JsonBody().with("repository", Path.of("").toAbsolutePath().toString()).with("range", range));
    }
}
