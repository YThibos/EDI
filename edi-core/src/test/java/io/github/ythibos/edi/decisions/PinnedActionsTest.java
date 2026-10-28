package io.github.ythibos.edi.decisions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PinnedActionsTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Path WORKFLOWS = REPOSITORY.resolve(".github/workflows");
    private static final Pattern USES = Pattern.compile("^\\s*(?:-\\s+)?uses:\\s.*$");
    private static final Pattern PINNED = Pattern.compile("^\\s*(?:-\\s+)?uses: [\\w.-]+/[\\w.-]+(?:/[\\w.-]+)*@[0-9a-f]{40} # v\\d+(?:\\.\\d+)*$");

    @Test
    void pinsEveryActionToAFullCommitWithItsTagBeside() throws IOException {
        List<WorkflowLine> uses = usesLines();

        assertThat(uses).isNotEmpty();
        assertThat(uses).filteredOn(action -> !PINNED.matcher(action.line()).matches()).isEmpty();
    }

    @Test
    void letsRenovateKeepTheActionsDigestsCurrent() throws IOException {
        JsonNode rules = new JsonMapper().readTree(Files.readString(REPOSITORY.resolve("renovate.json"))).path("packageRules");

        assertThat(rules.valueStream())
                .anyMatch(rule -> rule.path("matchManagers").valueStream().anyMatch(manager -> manager.asString().equals("github-actions"))
                        && rule.path("pinDigests").asBoolean(false));
    }

    private static List<WorkflowLine> usesLines() throws IOException {
        try (Stream<Path> workflows = Files.list(WORKFLOWS)) {
            return workflows.filter(file -> file.toString().endsWith(".yml") || file.toString().endsWith(".yaml")).sorted()
                    .flatMap(PinnedActionsTest::linesOf)
                    .filter(uses -> USES.matcher(uses.line()).matches())
                    .toList();
        }
    }

    private static Stream<WorkflowLine> linesOf(Path workflow) {
        try {
            return Files.readAllLines(workflow).stream().map(line -> new WorkflowLine(workflow.getFileName().toString(), line));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private record WorkflowLine(String workflow, String line) {

        @Override
        public String toString() {
            return workflow + ": " + line.strip();
        }
    }
}
