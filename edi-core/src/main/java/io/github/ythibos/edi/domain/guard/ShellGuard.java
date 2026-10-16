package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ShellGuard(String ediSocket, Set<String> baseBranches) {

    public ShellGuard {
        Objects.requireNonNull(ediSocket, "The guard knows the name of EDI's tmux socket.");
        baseBranches = Set.copyOf(baseBranches);
    }

    public Optional<GuardRefusal> check(String script) {
        List<ShellCommand> commands;
        try {
            commands = ShellScript.commandsIn(script);
        } catch (UnreadableCommand unreadable) {
            return Optional.of(new GuardRefusal(GuardRule.UNREADABLE_COMMAND,
                    unreadable.getMessage() + " EDI's guard refuses what it cannot read; run it as plainer commands."));
        }
        return ProcessSignals.refusalFor(commands).or(() -> firstRefusalAmong(commands));
    }

    private Optional<GuardRefusal> firstRefusalAmong(List<ShellCommand> commands) {
        return commands.stream().map(this::refusalFor).flatMap(Optional::stream).findFirst();
    }

    private Optional<GuardRefusal> refusalFor(ShellCommand command) {
        return TmuxSockets.refusalFor(command, ediSocket)
                .or(() -> GitPushes.refusalFor(command, baseBranches))
                .or(() -> MergingCommands.refusalFor(command));
    }
}
