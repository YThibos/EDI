package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "sitrep", description = "Prints the situation report: the boxed captain report, read-only, with a turn's items only when --turn gives them.")
final class SitrepCommand implements Callable<Integer> {

    private static final int ANSWERED = 0;
    private static final String SITREP_WITH_THE_TURN = "/api/report/sitrep";

    @ParentCommand
    private Edi edi;

    @Mixin
    private CoreExchange core;

    @Option(names = "--board", description = "Also opens the sitrep page of the EDI console in the captain's browser and prints its address.")
    private boolean board;

    @Option(names = "--turn", paramLabel = "-|FILE",
            description = "What this turn did, as for edi report --turn, shown in the sitrep's done and next, from standard input (-) or a file.")
    private String turn;

    @Override
    public Integer call() {
        int report = turn == null ? sitrep() : sitrepWithTheTurn();
        return report == ANSWERED && board ? core.post(ConsoleCommand.OPEN, new JsonBody()) : report;
    }

    private int sitrep() {
        return core.get(ReportCommand.sizedForThePane(ReportCommand.REPORT, edi.paneWidth()));
    }

    private int sitrepWithTheTurn() {
        return core.post(ReportCommand.sizedForThePane(SITREP_WITH_THE_TURN, edi.paneWidth()), core.contentOf(turn));
    }
}
