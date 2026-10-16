package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "task",
        description = "Adds, shows, lists and notes tasks.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {TaskAddCommand.class, TaskShowCommand.class, TaskListCommand.class, TaskNoteCommand.class})
final class TaskCommand extends CommandGroup {
}
