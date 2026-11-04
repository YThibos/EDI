package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "board",
        description = "Arms a research board, so EDI listens to the captain's answers on it, posts a new round on it, and shows its answered rounds.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {BoardArmCommand.class, BoardPostCommand.class, BoardShowCommand.class})
final class BoardCommand extends CommandGroup {
}
