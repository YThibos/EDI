package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ShellGuard(String ediSocket, Set<String> baseBranches, Set<String> unlandedBranches, Secrets secrets) {

    public ShellGuard {
        Objects.requireNonNull(ediSocket, "The guard knows the name of EDI's tmux socket.");
        baseBranches = Set.copyOf(baseBranches);
        unlandedBranches = Set.copyOf(unlandedBranches);
        Objects.requireNonNull(secrets, "The guard knows which secrets it keeps from a shell.");
    }

    public ShellGuard(String ediSocket, Set<String> baseBranches) {
        this(ediSocket, baseBranches, Set.of(), Secrets.NONE);
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
                .or(() -> BranchDeletions.refusalFor(command, unlandedBranches))
                .or(() -> MergingCommands.refusalFor(command))
                .or(() -> SecretReads.refusalFor(command, secrets));
    }
}
