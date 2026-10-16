package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParentCommand;

@Command(name = "sitrep", description = "Prints the situation report: the boxed captain report, read-only and without a turn's items.")
final class SitrepCommand implements Callable<Integer> {

    @ParentCommand
    private Edi edi;

    @Mixin
    private CoreExchange core;

    @Override
    public Integer call() {
        return core.get(ReportCommand.sizedForThePane(edi.paneWidth()));
    }
}
