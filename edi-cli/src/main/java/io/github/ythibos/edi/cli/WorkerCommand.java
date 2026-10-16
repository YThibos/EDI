package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "worker",
        description = "Lists workers, shows what a worker's pane shows, and stops or relaunches a worker.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {WorkerListCommand.class, WorkerPeekCommand.class, WorkerStopCommand.class, WorkerRelaunchCommand.class})
final class WorkerCommand extends CommandGroup {
}
