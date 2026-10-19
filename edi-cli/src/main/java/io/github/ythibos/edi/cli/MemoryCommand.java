package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "memory",
        description = "Checks the startup memory against its budget and lists what /stow should curate.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {MemoryCheckCommand.class, MemoryStowListCommand.class})
final class MemoryCommand extends CommandGroup {
}
