package io.github.ythibos.edi.cli;

import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "status", description = "Reports a worker's state to EDI: working, needs-decision, blocked, paused, done, failed or resolved.")
final class StatusCommand implements Callable<Integer> {

    private static final List<String> STANDARD_INPUT = List.of("-");

    @Mixin
    private CoreExchange core;

    @Parameters(index = "0", paramLabel = "STATE", description = "working, needs-decision, blocked, paused, done, failed or resolved.")
    private String state;

    @Parameters(index = "1..*", arity = "1..*", paramLabel = "TEXT", description = "What happened, in one line; - reads a longer text from standard input.")
    private List<String> text;

    @Option(names = "--key", paramLabel = "KEY", description = "The key of the decision or blocker this status opens or resolves.")
    private String key;

    @Option(names = "--until", paramLabel = "ISO-8601", description = "When a pause ends, such as 2026-10-16T14:30:00Z.")
    private String until;

    @Override
    public Integer call() {
        String line = text.equals(STANDARD_INPUT) ? core.contentOf("-") : String.join(" ", text);
        return core.post("/api/status", new JsonBody().with("state", state).with("key", key).with("until", until).with("text", line));
    }
}
