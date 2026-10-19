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
            description = "The card as JSON, {title, context, options: [{key, text, authorises}], recommend: {key, why}}; authorises names promote, drop or discard-work when choosing that option is the captain's word for it, from standard input (-) or a file.")
    private String card;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/decision", core.contentOf(card));
    }
}
