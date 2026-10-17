package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "board",
        description = "Arms a research board, so EDI listens to the captain's answers on it.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {BoardArmCommand.class})
final class BoardCommand extends CommandGroup {
}
