package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "rules", description = "Prints, for a period, how often each rule fired, when it last fired, and the rules that never fired.",
        subcommands = RulesFiredCommand.class)
final class RulesCommand implements Callable<Integer> {

    @ParentCommand
    private Edi edi;

    @Mixin
    private CoreExchange core;

    @Option(names = "--days", paramLabel = "N", defaultValue = "7", description = "The period: the last N days, 7 unless given.")
    private int days;

    @Override
    public Integer call() {
        return core.get("/api/rules?days=" + days);
    }

    Edi edi() {
        return edi;
    }
}
