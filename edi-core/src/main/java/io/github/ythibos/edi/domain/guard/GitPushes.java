package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class GitPushes {

    private static final Set<String> GLOBAL_OPTIONS_WITH_VALUES = Set.of("-C", "-c", "--git-dir", "--work-tree", "--namespace", "--super-prefix", "--config-env");
    static final Set<String> PUSH_OPTIONS_WITH_VALUES = Set.of("-o", "--push-option", "--repo", "--receive-pack", "--exec");
    private static final Set<String> PUSH_OPTION_FLAGS = Set.of("-o", "--push-option");
    private static final Set<String> FORCING_OPTIONS = Set.of("--force", "--force-if-includes", "--mirror");
    private static final Set<String> EVERY_BRANCH_OPTIONS = Set.of("--all", "--branches");
    private static final Set<String> DELETING_OPTIONS = Set.of("-d", "--delete");
    private static final List<String> MERGING_PUSH_OPTIONS = List.of("merge_when_pipeline_succeeds", "auto_merge");
    private static final String BRANCH_REFS = "refs/heads/";

    private GitPushes() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command, Set<String> baseBranches) {
        if (!command.runs("git")) {
            return Optional.empty();
        }
        List<String> afterGlobalOptions = afterGlobalOptions(command.argumentTexts());
        if (afterGlobalOptions.isEmpty() || !afterGlobalOptions.getFirst().equals("push")) {
            return Optional.empty();
        }
        return Push.of(afterGlobalOptions.subList(1, afterGlobalOptions.size())).refusal(baseBranches);
    }

    static List<String> deletedBranches(List<String> pushArguments) {
        return Push.of(pushArguments).deletedBranches();
    }

    static List<String> afterGlobalOptions(List<String> arguments) {
        int index = 0;
        while (index < arguments.size() && arguments.get(index).startsWith("-")) {
            index += GLOBAL_OPTIONS_WITH_VALUES.contains(arguments.get(index)) ? 2 : 1;
        }
        return arguments.subList(Math.min(index, arguments.size()), arguments.size());
    }

    private record Push(boolean forced, boolean everyBranch, boolean deleting, List<String> pushOptions, List<String> refspecs) {

        static Push of(List<String> arguments) {
            boolean forced = false;
            boolean everyBranch = false;
            boolean deleting = false;
            List<String> pushOptions = new ArrayList<>();
            List<String> positional = new ArrayList<>();
            int index = 0;
            while (index < arguments.size()) {
                String argument = arguments.get(index);
                if (PUSH_OPTIONS_WITH_VALUES.contains(argument)) {
                    if (PUSH_OPTION_FLAGS.contains(argument) && index + 1 < arguments.size()) {
                        pushOptions.add(arguments.get(index + 1));
                    }
                    index += 2;
                    continue;
                }
                if (argument.startsWith("--push-option=")) {
                    pushOptions.add(argument.substring("--push-option=".length()));
                } else if (isForcing(argument)) {
                    forced = true;
                } else if (EVERY_BRANCH_OPTIONS.contains(argument)) {
                    everyBranch = true;
                } else if (DELETING_OPTIONS.contains(argument)) {
                    deleting = true;
                } else if (!argument.startsWith("-")) {
                    positional.add(argument);
                }
                index++;
            }
            List<String> refspecs = positional.isEmpty() ? List.of() : positional.subList(1, positional.size());
            return new Push(forced, everyBranch, deleting, pushOptions, refspecs);
        }

        List<String> deletedBranches() {
            return refspecs.stream().filter(refspec -> deleting || deletesItsDestination(refspec)).map(Push::destinationOf).toList();
        }

        Optional<GuardRefusal> refusal(Set<String> baseBranches) {
            if (pushOptions.stream().anyMatch(option -> MERGING_PUSH_OPTIONS.stream().anyMatch(option.toLowerCase(Locale.ROOT)::contains))) {
                return Optional.of(MergingCommands.refusal());
            }
            if (forced || refspecs.stream().anyMatch(refspec -> refspec.startsWith("+"))) {
                return Optional.of(new GuardRefusal(GuardRule.FORCED_PUSH,
                        "a forced push rewrites published history, so none is made; push new commits on top instead."));
            }
            if (everyBranch) {
                return Optional.of(baseBranchPush("every branch"));
            }
            return refspecs.stream()
                    .map(Push::destinationOf)
                    .filter(baseBranches::contains)
                    .findFirst()
                    .map(base -> baseBranchPush(base));
        }

        private static boolean isForcing(String argument) {
            boolean shortFlagsWithForce = !argument.startsWith("--") && argument.startsWith("-") && argument.indexOf('f') > 0;
            return FORCING_OPTIONS.contains(argument) || argument.startsWith("--force-with-lease") || shortFlagsWithForce;
        }

        private static boolean deletesItsDestination(String refspec) {
            return refspec.startsWith(":") || refspec.startsWith("+:");
        }

        private static String destinationOf(String refspec) {
            String unforced = refspec.startsWith("+") ? refspec.substring(1) : refspec;
            String destination = unforced.contains(":") ? unforced.substring(unforced.lastIndexOf(':') + 1) : unforced;
            return destination.startsWith(BRANCH_REFS) ? destination.substring(BRANCH_REFS.length()) : destination;
        }

        private static GuardRefusal baseBranchPush(String target) {
            return new GuardRefusal(GuardRule.BASE_BRANCH_PUSH,
                    "a push to " + target + " lands work on a base branch, which only the captain does by merging a draft; push the task's own branch (I-03).");
        }
    }
}
