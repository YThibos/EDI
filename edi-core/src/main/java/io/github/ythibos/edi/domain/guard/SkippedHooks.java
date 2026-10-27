package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

final class SkippedHooks {

    private static final String NO_VERIFY = "--no-verify";
    private static final String SHORTEST_NO_VERIFY = "--no-v";
    private static final char COMMIT_NO_VERIFY_LETTER = 'n';
    private static final String OPTIONS_END = "--";
    private static final String HOOKS_PATH = "core.hookspath";
    private static final Set<String> CONFIGURING_OPTIONS = Set.of("-c", "--config-env");
    private static final String CONFIGURING_OPTION_PREFIX = "--config-env=";
    private static final OptionGrammar COMMIT = new OptionGrammar(Set.of("-m", "--message", "-F", "--file", "-c", "--reedit-message",
            "-C", "--reuse-message", "-t", "--template", "--author", "--date", "--cleanup", "--fixup", "--squash", "--trailer",
            "--pathspec-from-file"), "mFcCt", "mFcCtSu");
    private static final OptionGrammar MERGE = new OptionGrammar(Set.of("-m", "-F", "--file", "-s", "--strategy", "-X",
            "--strategy-option", "--cleanup", "--into-name"), "mFsX", "mFsXS");
    private static final OptionGrammar PUSH = new OptionGrammar(GitPushes.PUSH_OPTIONS_WITH_VALUES, "o", "o");
    private static final String COMMIT_MSG_CHECKS = "the commit-msg hook, which holds the work key at the head of the subject (I-10)"
            + " and keeps every co-author, agent and \"Generated with\" trailer out (I-09)";
    private static final String PRE_PUSH_CHECKS = "the pre-push hook, whose publish check keeps secrets (I-38) and -SNAPSHOT versions (I-11)"
            + " out of a push and pushes only to the registered forge (I-39)";
    private static final String EVERY_CHECK = "the commit-msg and pre-push hooks, which hold the work key (I-10), the bar on trailers (I-09),"
            + " the secret check (I-38), the SNAPSHOT check (I-11) and the registered forge (I-39)";
    private static final String WITHOUT_THE_FLAG = "run it without " + NO_VERIFY + " and fix what the hook names.";
    private static final String KEEP_THE_HOOKS_PATH = "leave core.hooksPath as the isolated copy sets it.";

    private SkippedHooks() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command) {
        if (!command.runs("git")) {
            return Optional.empty();
        }
        List<String> arguments = command.argumentTexts();
        List<String> afterGlobalOptions = GitPushes.afterGlobalOptions(arguments);
        if (overridesTheHooksPath(arguments.subList(0, arguments.size() - afterGlobalOptions.size()))) {
            return Optional.of(refusal("overriding core.hooksPath for one command", EVERY_CHECK, KEEP_THE_HOOKS_PATH));
        }
        if (afterGlobalOptions.isEmpty()) {
            return Optional.empty();
        }
        return refusalForSubcommand(afterGlobalOptions.getFirst(), afterGlobalOptions.subList(1, afterGlobalOptions.size()));
    }

    private static boolean overridesTheHooksPath(List<String> globalOptions) {
        return IntStream.range(0, globalOptions.size()).anyMatch(index -> setsTheHooksPathAt(globalOptions, index));
    }

    private static boolean setsTheHooksPathAt(List<String> globalOptions, int index) {
        String option = globalOptions.get(index);
        if (option.startsWith(CONFIGURING_OPTION_PREFIX)) {
            return namesTheHooksPath(option.substring(CONFIGURING_OPTION_PREFIX.length()));
        }
        return CONFIGURING_OPTIONS.contains(option) && index + 1 < globalOptions.size() && namesTheHooksPath(globalOptions.get(index + 1));
    }

    private static boolean namesTheHooksPath(String setting) {
        String key = setting.contains("=") ? setting.substring(0, setting.indexOf('=')) : setting;
        return key.toLowerCase(Locale.ROOT).equals(HOOKS_PATH);
    }

    private static GuardRefusal refusal(String bypass, String checks, String remedy) {
        return new GuardRefusal(GuardRule.SKIPPED_HOOKS, bypass + " skips " + checks + "; " + remedy);
    }

    private static Optional<GuardRefusal> refusalForSubcommand(String subcommand, List<String> arguments) {
        return switch (subcommand) {
            case "commit" -> refusalIf(commitSkipsItsHooks(arguments), "a commit with " + NO_VERIFY + " or -n", COMMIT_MSG_CHECKS);
            case "merge" -> refusalIf(namesNoVerify(MERGE.optionsAmong(arguments)), "a merge with " + NO_VERIFY, COMMIT_MSG_CHECKS);
            case "push" -> refusalIf(namesNoVerify(PUSH.optionsAmong(arguments)), "a push with " + NO_VERIFY, PRE_PUSH_CHECKS);
            default -> Optional.empty();
        };
    }

    private static Optional<GuardRefusal> refusalIf(boolean skips, String bypass, String checks) {
        return skips ? Optional.of(refusal(bypass, checks, WITHOUT_THE_FLAG)) : Optional.empty();
    }

    private static boolean commitSkipsItsHooks(List<String> arguments) {
        List<String> options = COMMIT.optionsAmong(arguments);
        return namesNoVerify(options) || options.stream().anyMatch(SkippedHooks::groupsTheCommitNoVerifyLetter);
    }

    private static boolean namesNoVerify(List<String> options) {
        return options.stream().anyMatch(SkippedHooks::isNoVerifyOrItsAbbreviation);
    }

    private static boolean groupsTheCommitNoVerifyLetter(String option) {
        return COMMIT.flagLettersOf(option).indexOf(COMMIT_NO_VERIFY_LETTER) >= 0;
    }

    private static boolean isNoVerifyOrItsAbbreviation(String option) {
        return option.startsWith(SHORTEST_NO_VERIFY) && NO_VERIFY.startsWith(option);
    }

    private record OptionGrammar(Set<String> optionsWithValues, String lettersTakingTheNextWord, String lettersTakingTheRest) {

        List<String> optionsAmong(List<String> arguments) {
            List<String> options = new ArrayList<>();
            int index = 0;
            while (index < arguments.size() && !arguments.get(index).equals(OPTIONS_END)) {
                String argument = arguments.get(index);
                if (argument.startsWith("-")) {
                    options.add(argument);
                }
                index += takesTheNextWord(argument) ? 2 : 1;
            }
            return options;
        }

        String flagLettersOf(String option) {
            if (!isShortOptionGroup(option)) {
                return "";
            }
            return option.substring(1, firstValueLetter(option));
        }

        private boolean takesTheNextWord(String argument) {
            if (optionsWithValues.contains(argument)) {
                return true;
            }
            int last = argument.length() - 1;
            return isShortOptionGroup(argument) && firstValueLetter(argument) == last && lettersTakingTheNextWord.indexOf(argument.charAt(last)) >= 0;
        }

        private static boolean isShortOptionGroup(String argument) {
            return argument.length() > 1 && argument.startsWith("-") && !argument.startsWith(OPTIONS_END);
        }

        private int firstValueLetter(String group) {
            for (int index = 1; index < group.length(); index++) {
                if (lettersTakingTheRest.indexOf(group.charAt(index)) >= 0) {
                    return index;
                }
            }
            return group.length();
        }
    }
}
