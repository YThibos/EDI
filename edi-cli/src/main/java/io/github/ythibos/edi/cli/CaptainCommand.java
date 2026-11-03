package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "captain",
        description = "Lists the captain's messages and records the captain's word for the commands that need it.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {CaptainSaidCommand.class, CaptainMessagesCommand.class})
final class CaptainCommand extends CommandGroup {
}
