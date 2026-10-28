package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class SecretReads {

    private static final Set<String> SHOW_TOKEN_OPTIONS = Set.of("-t", "--show-token");
    private static final String A_COMMAND = "this command";
    private static final String A_FILE_READ = "this read";
    private static final String A_SEARCH = "this file search";
    private static final String NAMES_A_SECRET_FILE = "it names a file that holds a secret";

    private SecretReads() {
    }

    public static Optional<GuardRefusal> refusalForReading(String file, Secrets secrets) {
        return secrets.areNamedIn(file) ? refused(A_FILE_READ, NAMES_A_SECRET_FILE) : Optional.empty();
    }

    public static Optional<GuardRefusal> refusalForSearching(BooleanSupplier reachesASecret) {
        try {
            return reachesASecret.getAsBoolean() ? refused(A_SEARCH, NAMES_A_SECRET_FILE) : Optional.empty();
        } catch (UntraceablePaths untraceable) {
            return untraced(A_SEARCH, untraceable);
        }
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command, Secrets secrets) {
        return secretFileNamedIn(command, secrets).or(() -> forgeTokenPrintedBy(command, secrets));
    }

    private static Optional<GuardRefusal> secretFileNamedIn(ShellCommand command, Secrets secrets) {
        try {
            return command.arguments().stream().anyMatch(secrets::areNamedIn) ? refused(A_COMMAND, NAMES_A_SECRET_FILE) : Optional.empty();
        } catch (UntraceablePaths untraceable) {
            return untraced(A_COMMAND, untraceable);
        }
    }

    private static Optional<GuardRefusal> forgeTokenPrintedBy(ShellCommand command, Secrets secrets) {
        return secrets.forgeTokens() && printsAForgeToken(command) ? refused(A_COMMAND, "it prints a forge's access token") : Optional.empty();
    }

    private static boolean printsAForgeToken(ShellCommand command) {
        List<String> arguments = command.argumentTexts();
        List<String> positional = arguments.stream().filter(argument -> !argument.startsWith("-")).toList();
        return switch (command.program()) {
            case "gh" -> startsWith(positional, "auth", "token");
            case "glab" -> startsWith(positional, "auth", "status") && arguments.stream().anyMatch(SHOW_TOKEN_OPTIONS::contains)
                    || startsWith(positional, "config", "get") && positional.contains("token");
            default -> false;
        };
    }

    private static boolean startsWith(List<String> positional, String first, String second) {
        return positional.size() >= 2 && positional.get(0).equals(first) && positional.get(1).equals(second);
    }

    private static Optional<GuardRefusal> refused(String act, String why) {
        return refusal(act + " touches a secret, because " + why);
    }

    private static Optional<GuardRefusal> untraced(String act, UntraceablePaths untraceable) {
        return refusal(act + " may touch a secret, because the guard cannot tell which files it reaches: " + untraceable.getMessage()
                + "; name the files more narrowly");
    }

    private static Optional<GuardRefusal> refusal(String what) {
        return Optional.of(new GuardRefusal(GuardRule.SECRET, what
                + "; secrets are read only by the core's adapters, at the moment of the call (I-32), and anything else that touches one needs"
                + " the captain's word in this session (I-06)."));
    }
}
