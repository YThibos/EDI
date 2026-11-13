package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(name = "done", description = "Records one thing this turn did that the captain should know, for the turn report's done.")
final class TurnDoneCommand extends TurnItemCommand {

    @Override
    String part() {
        return "done";
    }
}
