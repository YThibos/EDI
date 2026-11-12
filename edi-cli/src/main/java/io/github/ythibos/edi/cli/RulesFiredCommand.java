package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "fired", description = "Records that a rule fired outside the core, such as in a git hook; the core keeps it, or the home keeps it"
        + " until the core runs again.")
final class RulesFiredCommand implements Callable<Integer> {

    private static final int RECORDED = 0;

    @ParentCommand
    private RulesCommand rules;

    @Parameters(paramLabel = "RULE", description = "The rule's id from the catalogue, such as commit.work-key.")
    private String rule;

    @Option(names = "--outcome", defaultValue = RuleFirings.REFUSED, description = "refused, warned or allowed; refused unless given.")
    private String outcome;

    @Option(names = "--reason", required = true, description = "Why it fired, in a short sentence.")
    private String reason;

    @Override
    public Integer call() {
        RuleFirings.throughTheCore(rules.edi()).fired(rule, outcome, reason);
        return RECORDED;
    }
}
