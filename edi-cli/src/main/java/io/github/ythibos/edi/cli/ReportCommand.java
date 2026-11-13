package io.github.ythibos.edi.cli;

import java.util.OptionalInt;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParentCommand;

@Command(name = "report", description = "Prints the turn report: only what needs the captain, every open decision and review,"
        + " and the items this turn recorded with edi turn; the stop hook hands it over itself, and edi sitrep prints the overview.")
final class ReportCommand implements Callable<Integer> {

    static final String REPORT = "/api/report";

    @ParentCommand
    private Edi edi;

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.post(sizedForThePane(REPORT, edi.paneWidth()), new JsonBody());
    }

    static String sizedForThePane(String report, OptionalInt paneWidth) {
        return paneWidth.isPresent() ? report + "?paneWidth=" + paneWidth.getAsInt() : report;
    }
}
