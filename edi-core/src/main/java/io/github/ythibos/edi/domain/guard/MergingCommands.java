package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class MergingCommands {

    private static final Set<String> FORGE_OPTIONS_WITH_VALUES = Set.of("-R", "--repo", "-H", "--hostname");
    private static final Set<String> GITHUB_MERGES = Set.of("pr merge");
    private static final Set<String> GITLAB_MERGES = Set.of("mr merge", "mr accept");
    private static final List<String> MERGING_API_WORDS = List.of("mergepullrequest", "automerge", "merge_when_pipeline_succeeds");
    private static final String API = "api";
    private static final String MERGE_SEGMENT = "merge";

    private MergingCommands() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command) {
        boolean merges = switch (command.program()) {
            case "gh" -> isAForgeMergeCall(command.argumentTexts(), GITHUB_MERGES);
            case "glab" -> isAForgeMergeCall(command.argumentTexts(), GITLAB_MERGES);
            default -> false;
        };
        return merges ? Optional.of(refusal()) : Optional.empty();
    }

    static GuardRefusal refusal() {
        return new GuardRefusal(GuardRule.MERGE, "Only the captain merges a change or arms a merge (I-03).");
    }

    private static boolean isAForgeMergeCall(List<String> arguments, Set<String> merges) {
        List<String> positional = positional(arguments);
        if (positional.size() >= 2 && merges.contains(positional.get(0) + " " + positional.get(1))) {
            return true;
        }
        return !positional.isEmpty() && positional.getFirst().equals(API) && arguments.stream().anyMatch(MergingCommands::namesAMergeEndpoint);
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

    private static boolean namesAMergeEndpoint(String argument) {
        String lowerCase = argument.toLowerCase(Locale.ROOT);
        boolean mergePath = List.of(lowerCase.split("[/?&\\s]")).contains(MERGE_SEGMENT);
        return mergePath || MERGING_API_WORDS.stream().anyMatch(lowerCase::contains);
    }
}
