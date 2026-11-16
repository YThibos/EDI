package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.ythibos.edi.domain.rule.Rule;

final class MergingCommands {

    private static final Set<String> FORGE_OPTIONS_WITH_VALUES = Set.of("-R", "--repo", "-H", "--hostname");
    private static final Set<String> GITHUB_MERGES = Set.of("pr merge");
    private static final Set<String> GITLAB_MERGES = Set.of("mr merge", "mr accept");
    private static final String API = "api";

    private MergingCommands() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command) {
        Optional<String> merge = switch (command.program()) {
            case "gh" -> mergingActThroughTheForge(command, GITHUB_MERGES);
            case "glab" -> mergingActThroughTheForge(command, GITLAB_MERGES);
            default -> Optional.empty();
        };
        return merge.map(MergingCommands::refusal);
    }

    static GuardRefusal refusal(String merge) {
        return new GuardRefusal(Rule.MERGE, merge,
                "only the captain merges a change or arms a merge; open a draft and leave the merge to the captain (I-03).");
    }

    private static Optional<String> mergingActThroughTheForge(ShellCommand command, Set<String> merges) {
        List<String> arguments = command.argumentTexts();
        List<String> positional = positional(arguments);
        Optional<String> subcommand = subcommandOf(positional).filter(merges::contains);
        if (subcommand.isPresent()) {
            return Optional.of("a merge through " + command.program() + " " + subcommand.get());
        }
        return callsTheApi(positional) ? ForgeApiCall.of(arguments).mergingAct() : Optional.empty();
    }

    private static List<String> positional(List<String> arguments) {
        List<String> positional = new ArrayList<>();
        int index = 0;
        while (index < arguments.size()) {
            String argument = arguments.get(index);
            if (FORGE_OPTIONS_WITH_VALUES.contains(argument)) {
                index += 2;
            } else {
                if (!argument.startsWith("-")) {
                    positional.add(argument);
                }
                index++;
            }
        }
        return positional;
    }

    private static Optional<String> subcommandOf(List<String> positional) {
        return positional.size() >= 2 ? Optional.of(positional.get(0) + " " + positional.get(1)) : Optional.empty();
    }

    private static boolean callsTheApi(List<String> positional) {
        return !positional.isEmpty() && positional.getFirst().equals(API);
    }
}
