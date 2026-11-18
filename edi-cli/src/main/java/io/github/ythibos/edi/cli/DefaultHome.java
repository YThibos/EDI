package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record DefaultHome(Path userHome, Path workingDirectory) {

    static final String VARIABLE = "EDI_HOME";
    private static final List<String> TEST_RUNNER_MARKS = List.of("EDI_TEST_RUN", "MAVEN_PROJECTBASEDIR", "NODE_TEST_CONTEXT", "VITEST",
            "JEST_WORKER_ID", "PYTEST_CURRENT_TEST", "BATS_TEST_FILENAME");

    Path fallenBackToUnder(Map<String, String> environment) {
        Path home = userHome.resolve(".edi");
        whyTheFallbackIsRefused(environment, home).ifPresent(reason -> {
            throw new DefaultHomeRefused("edi refuses to fall back to the default EDI home " + home + ": " + reason + ", where a fallback would reach"
                    + " the captain's own core and store. Set " + VARIABLE + " to a throwaway home this run created.");
        });
        return home;
    }

    private Optional<String> whyTheFallbackIsRefused(Map<String, String> environment, Path home) {
        if (workingDirectory.startsWith(home.resolve("worktrees"))) {
            return Optional.of("it runs inside a worker's isolated copy, " + workingDirectory);
        }
        if (workingDirectory.startsWith(validationHome())) {
            return Optional.of("it runs in a validation's working directory, " + workingDirectory);
        }
        return testRunnerMarkIn(environment).map(mark -> "it runs under a test runner, which sets " + mark);
    }

    private Path validationHome() {
        return userHome.resolve(".no-mistakes");
    }

    private static Optional<String> testRunnerMarkIn(Map<String, String> environment) {
        return TEST_RUNNER_MARKS.stream().filter(environment::containsKey).findFirst();
    }
}
