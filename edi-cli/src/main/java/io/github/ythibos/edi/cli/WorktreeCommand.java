package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "worktree",
        description = "Acts on a task's isolated copy.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {WorktreeDiscardCommand.class})
final class WorktreeCommand extends CommandGroup {
}
