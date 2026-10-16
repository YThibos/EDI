package io.github.ythibos.edi.cli;

import java.util.OptionalInt;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "report", description = "Prints the boxed captain report that ends a turn: the open decisions, what this turn did, what runs and what waits.")
final class ReportCommand implements Callable<Integer> {

    private static final String REPORT = "/api/report";

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
        String report = sizedForThePane(edi.paneWidth());
        return turn == null ? core.get(report) : core.post(report, core.contentOf(turn));
    }

    static String sizedForThePane(OptionalInt paneWidth) {
        return paneWidth.isPresent() ? REPORT + "?paneWidth=" + paneWidth.getAsInt() : REPORT;
    }
}
