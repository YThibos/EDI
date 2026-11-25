package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "tracker",
        description = "Moves a ticket through the project's workflow and lists the tickets that disagree with the repository.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {TrackerWalkCommand.class, TrackerDriftCommand.class})
final class TrackerCommand extends CommandGroup {
}
