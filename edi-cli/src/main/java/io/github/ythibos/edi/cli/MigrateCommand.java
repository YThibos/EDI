package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "migrate",
        description = "Imports another harness's state into EDI.",
        synopsisSubcommandLabel = "SOURCE",
        subcommands = MigrateFirstofficerCommand.class)
final class MigrateCommand extends CommandGroup {
}
