package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "said", description = "Records the captain's word in this session, verbatim, as he typed it: on one task, or on matters that"
        + " concern no single task, such as touching a secret.")
final class CaptainSaidCommand implements Callable<Integer> {

    private static final String STANDARD_INPUT = "-";

    @Mixin
    private CoreExchange core;

    @Option(names = "--text", required = true, paramLabel = "WORDS",
            description = "The captain's words exactly as he typed them, his whole message or one whole line of it; - reads them from standard input.")
    private String text;

    @Option(names = "--task", paramLabel = "TASK", description = "The task his word is on: a task id, or the D number of its decision.")
    private String task;

    @Override
    public Integer call() {
        String words = text.equals(STANDARD_INPUT) ? core.contentOf(STANDARD_INPUT) : text;
        return core.post("/api/captain/said", new JsonBody().with("text", words).with("task", task));
    }
}
