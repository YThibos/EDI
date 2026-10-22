package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "arm", description = "Has EDI listen to a Lavish research board in your task's directory and pass each round of answers to your inbox.")
final class BoardArmCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "HTML", description = "The board's HTML file, in the task's directory.")
    private Path board;

    @Option(names = "--task", required = true, paramLabel = "TASK", description = "The task the board belongs to: your own.")
    private String task;

    @Option(names = "--link", required = true, paramLabel = "URL", description = "The link lavish-axi printed for the board, which the captain opens.")
    private String link;

    @Option(names = "--questions", required = true, paramLabel = "N",
            description = "How many questions this round puts before the captain, not counting its card or the decisions it binds; arming the board again posts a new round.")
    private int questions;

    @Option(names = "--bind", split = ",", paramLabel = "D58,D59", description = "Open decisions the board answers for the captain.")
    private List<String> decisions;

    @Option(names = "--card", paramLabel = "-|FILE",
            description = "The decision card this round puts before the captain, as JSON, {question, title, context, options: [{key, text, authorises}], recommend: {key, why}}, from standard input (-) or a file; question is the board question that answers it, which the answer's data names as question; EDI raises it on your task and binds it, so choosing on the board is the captain's word.")
    private String card;

    @Override
    public Integer call() {
        return core.post("/api/boards", new JsonBody()
                .with("file", board.toAbsolutePath().normalize().toString())
                .with("task", task)
                .with("link", link)
                .with("questions", questions)
                .with("bind", decisions)
                .withJson("card", card == null ? null : core.contentOf(card)));
    }
}
