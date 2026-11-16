package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(name = "next", description = "Records one thing that comes next, for the turn report's next.")
final class TurnNextCommand extends TurnItemCommand {

    @Override
    String part() {
        return "next";
    }
}
