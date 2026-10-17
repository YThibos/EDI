package io.github.ythibos.edi.cli;

import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "dispatch", description = "Launches a worker on a task in its own isolated copy, routed by the task's dispatch category.")
final class DispatchCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Mixin
    private DispatchOptions options;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/dispatch", options.body(core));
    }

    static final class DispatchOptions {

        @Option(names = "--category", paramLabel = "CATEGORY", description = "The dispatch category; a task dispatched before keeps its category without it.")
        private String category;

        @Option(names = "--spec", paramLabel = "-|FILE", description = "The build instructions for the brief, from standard input (-) or a file.")
        private String spec;

        @Option(names = "--files", split = ",", paramLabel = "FILE", description = "Every file a mechanical task touches.")
        private List<String> files;

        @Option(names = "--override-model", paramLabel = "MODEL", description = "The model the captain asked for, outranking the dispatch rules.")
        private String overrideModel;

        @Option(names = "--override-effort", paramLabel = "EFFORT", description = "The effort the captain asked for: low, medium or high.")
        private String overrideEffort;

        @Option(names = "--reason", paramLabel = "REASON", description = "The captain's reason for the override.")
        private String reason;

        @Option(names = "--cause-unknown", description = "A bugfix whose cause is not established yet, which research may plan first.")
        private boolean causeUnknown;

        boolean namesACategory() {
            return category != null;
        }

        JsonBody body(CoreExchange core) {
            return new JsonBody()
                    .with("category", category)
                    .with("spec", spec == null ? null : core.contentOf(spec))
                    .with("files", files)
                    .with("overrideModel", overrideModel)
                    .with("overrideEffort", overrideEffort)
                    .with("reason", reason)
                    .with("causeUnknown", causeUnknown);
        }
    }
}
