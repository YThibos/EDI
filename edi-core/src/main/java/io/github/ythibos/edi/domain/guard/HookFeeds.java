package io.github.ythibos.edi.domain.guard;

import java.util.Optional;

import io.github.ythibos.edi.domain.rule.Rule;

final class HookFeeds {

    private static final String EDI = "edi";
    private static final String HOOK = "hook";

    private HookFeeds() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command) {
        return command.runs(EDI) && subcommandOf(command).filter(HOOK::equals).isPresent() ? Optional.of(refusal()) : Optional.empty();
    }

    private static Optional<String> subcommandOf(ShellCommand command) {
        return command.argumentTexts().stream().filter(argument -> !argument.startsWith("-")).findFirst();
    }

    private static GuardRefusal refusal() {
        return new GuardRefusal(Rule.HOOK_FROM_THE_SHELL, "only Claude Code feeds the hooks, with what really happened in the session;"
                + " a hook fed from the primary's shell would let made-up text pass as the captain's words.");
    }
}
