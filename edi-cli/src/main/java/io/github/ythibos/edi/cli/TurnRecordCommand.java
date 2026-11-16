package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "record", description = "Records several of this turn's items at once, as JSON {done: [{projects, text}], next: [{projects, text}],"
        + " tasks: {TASK: {text, state, projects}}}, from standard input (-) or a file.")
final class TurnRecordCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "-|FILE", description = "The turn's items: - for standard input, or a file.")
    private String items;

    @Override
    public Integer call() {
        return core.post(TurnCommand.TURN, core.contentOf(items));
    }
}
