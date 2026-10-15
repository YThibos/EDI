package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "raise", description = "Raises a decision on a task from its card and prints its D number.")
final class DecisionRaiseCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "The task the decision holds; raising it again updates the card and keeps the number.")
    private String task;

    @Option(names = "--card", required = true, paramLabel = "-|FILE",
            description = "The card as JSON, {title, context, options: [{key, text}], recommend: {key, why}}, from standard input (-) or a file.")
    private String card;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/decision", core.contentOf(card));
    }
}
