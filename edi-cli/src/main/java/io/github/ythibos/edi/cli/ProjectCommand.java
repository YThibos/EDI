package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "project",
        description = "Reads the project registry.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = ProjectListCommand.class)
final class ProjectCommand extends CommandGroup {
}
