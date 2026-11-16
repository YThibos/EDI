package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "plan",
        description = "Enters a plan of slices, each a task with its own key and one wait, and shows a plan's slices with their stages and waits.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {PlanAddCommand.class, PlanShowCommand.class})
final class PlanCommand extends CommandGroup {
}
