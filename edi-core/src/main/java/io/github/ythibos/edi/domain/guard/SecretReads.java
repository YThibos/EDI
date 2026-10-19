package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class SecretReads {

    private static final Set<String> SHOW_TOKEN_OPTIONS = Set.of("-t", "--show-token");

    private SecretReads() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command, Secrets secrets) {
        if (command.argumentTexts().stream().anyMatch(secrets::areNamedIn)) {
            return refused("it names a file that holds a secret");
        }
        if (secrets.forgeTokens() && printsAForgeToken(command)) {
            return refused("it prints a forge's access token");
        }
        return Optional.empty();
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

    private static Optional<GuardRefusal> refused(String why) {
        return Optional.of(new GuardRefusal(GuardRule.SECRET, "this command touches a secret, because " + why
                + "; secrets are read only by EDI's adapters, at the moment of the call (I-32), and anything else that touches one needs"
                + " the captain's word in this session (I-06)."));
    }
}
