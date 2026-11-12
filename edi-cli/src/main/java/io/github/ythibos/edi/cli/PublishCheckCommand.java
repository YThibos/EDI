package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "publish-check", description = "Checks the outgoing commits of this repository for secrets, personal material and -SNAPSHOT versions before a push.")
final class PublishCheckCommand implements Callable<Integer> {

    private static final String UPSTREAM_TO_HEAD = "@{upstream}..HEAD";
    private static final String PUBLISH_CHECK_REACHABLE = "publish.check-reachable";

    @ParentCommand
    private Edi edi;

    @Mixin
    private CoreExchange core;

    @Option(names = "--range", paramLabel = "A..B", description = "The outgoing commits; by default those between the branch's upstream and HEAD.")
    private String range = UPSTREAM_TO_HEAD;

    @Option(names = "--excluding", paramLabel = "REVISION", description = "A revision whose commits are not outgoing, such as the base branch; repeatable.")
    private List<String> excluding;

    @Option(names = "--remote-url", paramLabel = "URL", description = "Where the push goes; EDI refuses a push from an isolated copy to any "
            + "repository but its project's registered one.")
    private String remoteUrl;

    @Override
    public Integer call() {
        return core.post("/api/publish-checks", new JsonBody()
                .with("repository", Path.of("").toAbsolutePath().toString())
                .with("range", range)
                .with("excluding", excluding)
                .with("remoteUrl", remoteUrl), this::refusedUnchecked);
    }

    private void refusedUnchecked(String unreachable) {
        RuleFirings.intoTheSpool(edi).fired(PUBLISH_CHECK_REACHABLE, RuleFirings.REFUSED,
                "The publish check could not run, so nothing it checks goes out: " + unreachable);
    }
}
