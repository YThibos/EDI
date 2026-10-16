package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "worker",
        description = "Lists workers and shows what a worker's pane shows.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {WorkerListCommand.class, WorkerPeekCommand.class})
final class WorkerCommand extends CommandGroup {
}
