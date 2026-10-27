package io.github.ythibos.edi.domain.worker;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public enum HeavyRun {
    NATIVE_BUILD("native build"),
    TEST_RUN("test run");

    private static final String MAVEN_LAUNCHER = "org.codehaus.plexus.classworlds.launcher.Launcher";
    private static final String NATIVE_PROFILE = "native";
    private static final String PROFILE_FLAG = "-P";
    private static final Set<String> PHASES_THAT_RUN_TESTS = Set.of("test", "package", "integration-test", "verify", "install", "deploy");

    private final String words;

    HeavyRun(String words) {
        this.words = words;
    }

    public static Optional<HeavyRun> of(List<String> arguments) {
        if (!arguments.contains(MAVEN_LAUNCHER)) {
            return Optional.empty();
        }
        if (profilesNamedIn(arguments).anyMatch(NATIVE_PROFILE::equals)) {
            return Optional.of(NATIVE_BUILD);
        }
        return arguments.stream().anyMatch(PHASES_THAT_RUN_TESTS::contains) ? Optional.of(TEST_RUN) : Optional.empty();
    }

    public String words() {
        return words;
    }

    private static Stream<String> profilesNamedIn(List<String> arguments) {
        return IntStream.range(0, arguments.size())
                .mapToObj(index -> profileListAt(arguments, index))
                .flatMap(list -> Arrays.stream(list.split(",")));
    }

    private static String profileListAt(List<String> arguments, int index) {
        String argument = arguments.get(index);
        if (argument.equals(PROFILE_FLAG)) {
            return index + 1 < arguments.size() ? arguments.get(index + 1) : "";
        }
        return argument.startsWith(PROFILE_FLAG) ? argument.substring(PROFILE_FLAG.length()) : "";
    }
}
