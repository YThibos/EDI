package io.github.ythibos.edi.cli;

import picocli.CommandLine.Command;

@Command(
        name = "decision",
        description = "Raises, answers and lists the captain's decisions.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {DecisionRaiseCommand.class, DecisionAnswerCommand.class, DecisionListCommand.class})
final class DecisionCommand extends CommandGroup {
}
