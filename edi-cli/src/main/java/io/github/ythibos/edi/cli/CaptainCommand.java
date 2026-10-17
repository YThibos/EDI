package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "captain",
        description = "Records the captain's word for the commands that need it.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {CaptainSaidCommand.class})
final class CaptainCommand extends CommandGroup {
}
