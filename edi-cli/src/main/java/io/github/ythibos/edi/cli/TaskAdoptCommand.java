package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "adopt", description = "Takes over a task still open in a firstofficer home: imports it, makes its isolated copy on the same branch"
        + " from EDI's own clone, and records the Claude Code session its next dispatch resumes; the old copy is left as it was.")
final class TaskAdoptCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "The task's id in the old home's backlog.")
    private String task;

    @Option(names = "--from", required = true, paramLabel = "HOME", description = "The firstofficer home whose backlog holds the task.")
    private Path from;

    @Option(names = "--branch", required = true, paramLabel = "BRANCH", description = "The task's branch, such as feature/EDI-14; it names the work.")
    private String branch;

    @Option(names = "--worktree", required = true, paramLabel = "PATH", description = "The old harness's copy that has the branch checked out.")
    private Path worktree;

    @Option(names = "--session", paramLabel = "UUID", description = "The Claude Code session that worked in the old copy, resumed by the next dispatch.")
    private String session;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/adopt", new JsonBody()
                .with("from", absolute(from))
                .with("branch", branch)
                .with("worktree", absolute(worktree))
                .with("session", session));
    }

    private static String absolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
