package io.github.ythibos.edi.cli;

import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "add", description = "Queues a new task and prints its id.")
final class TaskAddCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Option(names = "--kind", required = true, paramLabel = "KIND", description = "ship, scout or captain.")
    private String kind;

    @Option(names = "--project", split = ",", paramLabel = "PROJECT", description = "The registered project or projects the task works on.")
    private List<String> projects;

    @Option(names = "--title", required = true, paramLabel = "TITLE", description = "What the task delivers, in one line.")
    private String title;

    @Option(names = "--id", paramLabel = "ID", description = "A kebab-case id; by default one is made from the title.")
    private String id;

    @Option(names = "--key", paramLabel = "KEY", description = "The work key, such as EDI-3.")
    private String key;

    @Option(names = "--base", paramLabel = "BRANCH", description = "The branch the work starts from, when not the project's own.")
    private String base;

    @Option(names = "--blocked-by", split = ",", paramLabel = "TASK", description = "Tasks that must be done first.")
    private List<String> blockedBy;

    @Option(names = "--delivery", paramLabel = "MODE",
            description = "How this task's work reaches the captain when not the project's own way: draft-review, local-branch or local-apply.")
    private String delivery;

    @Option(names = "--body", paramLabel = "-|FILE", description = "The task's notes, from standard input (-) or a file.")
    private String body;

    @Override
    public Integer call() {
        return core.post("/api/tasks", new JsonBody()
                .with("id", id)
                .with("kind", kind)
                .with("projects", projects)
                .with("title", title)
                .with("key", key)
                .with("base", base)
                .with("blockedBy", blockedBy)
                .with("delivery", delivery)
                .with("body", body == null ? null : core.contentOf(body)));
    }
}
