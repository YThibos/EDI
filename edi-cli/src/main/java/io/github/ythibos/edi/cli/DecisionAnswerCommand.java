package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "answer", description = "Records the captain's answer to a decision, verbatim, from a message they typed or a dialog they answered.")
final class DecisionAnswerCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "DECISION", description = "A D number, or the id of the task the decision holds.")
    private String decision;

    @Mixin
    private CaptainsWordsCited words;

    @Option(names = "--option", paramLabel = "KEY", description = "The option the captain chose, such as A.")
    private String option;

    @Option(names = "--interpretation", paramLabel = "TEXT", description = "How the answer is understood.")
    private String interpretation;

    @Override
    public Integer call() {
        return core.post("/api/decisions/" + CoreExchange.encoded(decision) + "/answer",
                words.addedTo(new JsonBody(), core).with("option", option).with("interpretation", interpretation));
    }
}
