package io.github.ythibos.edi.cli;

import java.util.OptionalInt;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "report", description = "Prints the turn report that ends a turn: only what needs the captain, every open decision and review,"
        + " and what this turn did and does next; edi sitrep prints the overview.")
final class ReportCommand implements Callable<Integer> {

    static final String REPORT = "/api/report";

    @ParentCommand
    private Edi edi;

    @Mixin
    private CoreExchange core;

    @Option(names = "--turn", paramLabel = "-|FILE",
            description = "What this turn did, as JSON {done: [{projects, text}], next: [{projects, text}], tasks: {TASK: {text, state, projects}}},"
                    + " from standard input (-) or a file.")
    private String turn;

    @Override
    public Integer call() {
        String report = sizedForThePane(REPORT, edi.paneWidth());
        return turn == null ? core.post(report, new JsonBody()) : core.post(report, core.contentOf(turn));
    }

    static String sizedForThePane(String report, OptionalInt paneWidth) {
        return paneWidth.isPresent() ? report + "?paneWidth=" + paneWidth.getAsInt() : report;
    }
}
