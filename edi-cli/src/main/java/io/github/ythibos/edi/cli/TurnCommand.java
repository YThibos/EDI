package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "turn",
        description = "Records this turn's items for its turn report as they happen; the stop hook renders the report from them and hands it over to paste.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {TurnDoneCommand.class, TurnNextCommand.class, TurnRecordCommand.class})
final class TurnCommand extends CommandGroup {

    static final String TURN = "/api/turn";
}
