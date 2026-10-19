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

    @Option(names = "--bind", split = ",", paramLabel = "D58,D59", description = "Open decisions the board answers for the captain.")
    private List<String> decisions;

    @Override
    public Integer call() {
        return core.post("/api/boards", new JsonBody()
                .with("file", board.toAbsolutePath().normalize().toString())
                .with("task", task)
                .with("bind", decisions));
    }
}
