package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "adopt", description = "Takes over a task still open in a firstofficer home: imports it, registers its isolated copy, moved under"
        + " EDI's worktrees with its uncommitted work when no process holds it, and records the Claude Code session its next dispatch resumes.")
final class TaskAdoptCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "The task's id in the old home's backlog.")
    private String task;

    @Option(names = "--from", paramLabel = "HOME", description = "The firstofficer home whose backlog holds the task; by default the home whose"
            + " clone the copy belongs to.")
    private Path from;

    @Option(names = "--branch", required = true, paramLabel = "BRANCH", description = "The task's branch, such as feature/EDI-14; it names the work.")
    private String branch;

    @Option(names = "--worktree", required = true, paramLabel = "PATH", description = "The old harness's copy that has the branch checked out.")
    private Path worktree;

    @Option(names = "--session", paramLabel = "UUID", description = "The Claude Code session that worked in the old copy, resumed by the next dispatch.")
    private String session;

    @Override
    public Integer call() {
        return core.postAwaitingLongWork("/api/tasks/" + CoreExchange.encoded(task) + "/adopt", new JsonBody()
                .with("from", from == null ? null : absolute(from))
                .with("branch", branch)
                .with("worktree", absolute(worktree))
                .with("session", session));
    }

    private static String absolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
