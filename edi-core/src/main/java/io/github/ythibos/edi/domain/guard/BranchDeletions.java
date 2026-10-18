package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class BranchDeletions {

    private BranchDeletions() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command, Set<String> unlandedBranches) {
        if (!command.runs("git")) {
            return Optional.empty();
        }
        List<String> afterGlobalOptions = GitPushes.afterGlobalOptions(command.argumentTexts());
        if (afterGlobalOptions.isEmpty()) {
            return Optional.empty();
        }
        List<String> arguments = afterGlobalOptions.subList(1, afterGlobalOptions.size());
        List<String> deleted = switch (afterGlobalOptions.getFirst()) {
            case "branch" -> deletedByBranch(arguments);
            case "push" -> GitPushes.deletedBranches(arguments);
            default -> List.of();
        };
        return deleted.stream().filter(unlandedBranches::contains).findFirst().map(BranchDeletions::refusal);
    }

    private static List<String> deletedByBranch(List<String> arguments) {
        if (arguments.stream().noneMatch(BranchDeletions::deletes)) {
            return List.of();
        }
        return arguments.stream().filter(argument -> !argument.startsWith("-")).toList();
    }

    private static boolean deletes(String argument) {
        boolean shortFlagsWithDelete = !argument.startsWith("--") && argument.startsWith("-")
                && (argument.indexOf('d') > 0 || argument.indexOf('D') > 0);
        return argument.equals("--delete") || shortFlagsWithDelete;
    }

    private static GuardRefusal refusal(String branch) {
        return new GuardRefusal(GuardRule.UNLANDED_BRANCH_DELETION, "it deletes " + branch
                + ", the branch of a task whose work has not landed, and deleting it needs the captain's word in this session (I-06).");
    }
}
