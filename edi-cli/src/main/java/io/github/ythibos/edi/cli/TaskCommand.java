package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "task",
        description = "Adds, shows, lists, notes, promotes and drops tasks.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {TaskAddCommand.class, TaskShowCommand.class, TaskListCommand.class, TaskNoteCommand.class, TaskPromoteCommand.class,
            TaskDropCommand.class})
final class TaskCommand extends CommandGroup {
}
