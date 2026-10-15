package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "answer", description = "Records the captain's answer to a decision, verbatim.")
final class DecisionAnswerCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "DECISION", description = "A D number, or the id of the task the decision holds.")
    private String decision;

    @Option(names = "--text", required = true, paramLabel = "WORDS", description = "The captain's own words, exactly as given.")
    private String text;

    @Option(names = "--option", paramLabel = "KEY", description = "The option the captain chose, such as A.")
    private String option;

    @Option(names = "--interpretation", paramLabel = "TEXT", description = "How the answer is understood.")
    private String interpretation;

    @Override
    public Integer call() {
        return core.post("/api/decisions/" + CoreExchange.encoded(decision) + "/answer",
                new JsonBody().with("text", text).with("option", option).with("interpretation", interpretation));
    }
}
