package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "sitrep", description = "Prints the situation report: the boxed captain report, read-only and without a turn's items.")
final class SitrepCommand implements Callable<Integer> {

    private static final int ANSWERED = 0;

    @ParentCommand
    private Edi edi;

    @Mixin
    private CoreExchange core;

    @Option(names = "--board", description = "Also opens the sitrep page of the EDI console in the captain's browser and prints its address.")
    private boolean board;

    @Override
    public Integer call() {
        int report = core.get(ReportCommand.sizedForThePane(edi.paneWidth()));
        return report == ANSWERED && board ? core.post(ConsoleCommand.OPEN, new JsonBody()) : report;
    }
}
