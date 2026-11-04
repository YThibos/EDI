package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "worker",
        description = "Lists workers, shows a worker from the store or what its pane shows, and stops or relaunches a worker.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {WorkerListCommand.class, WorkerShowCommand.class, WorkerPeekCommand.class, WorkerStopCommand.class, WorkerRelaunchCommand.class})
final class WorkerCommand extends CommandGroup {
}
