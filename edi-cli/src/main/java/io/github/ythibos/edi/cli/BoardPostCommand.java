package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "post", description = "Puts a new round of a listening research board before the captain, when its scout cannot arm it again.")
final class BoardPostCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "HTML", description = "The listening board's HTML file.")
    private Path board;

    @Option(names = "--link", required = true, paramLabel = "URL", description = "The link lavish-axi printed for the board, which the captain opens.")
    private String link;

    @Option(names = "--questions", required = true, paramLabel = "N",
            description = "How many questions the round puts before the captain, not counting the decisions it is bound to.")
    private int questions;

    @Override
    public Integer call() {
        return core.post("/api/boards/rounds", new JsonBody()
                .with("file", board.toAbsolutePath().normalize().toString())
                .with("link", link)
                .with("questions", questions));
    }
}
