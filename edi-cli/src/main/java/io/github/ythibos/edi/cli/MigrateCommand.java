package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "migrate",
        description = "Plans the import of another harness's state.",
        synopsisSubcommandLabel = "SOURCE",
        subcommands = MigrateFirstofficerCommand.class)
final class MigrateCommand extends CommandGroup {
}
