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
import tools.jackson.dataformat.yaml.YAMLMapper;

class PinnedActionsTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Path WORKFLOWS = REPOSITORY.resolve(".github/workflows");
    private static final YAMLMapper YAML = new YAMLMapper();
    private static final Pattern USES_LINE = Pattern.compile("^\\s*(?:-\\s+)?uses:\\s.*$");
    private static final Pattern TAG_BESIDE = Pattern.compile("^\\s*(?:-\\s+)?uses: \\S+ # v\\d+(?:\\.\\d+)*$");
    private static final Pattern PINNED_ACTION = Pattern.compile("[\\w.-]+/[\\w.-]+(?:/[\\w.-]+)*@[0-9a-f]{40}");
    private static final Pattern PINNED_IMAGE = Pattern.compile("(?:docker://)?[\\w./:-]+@sha256:[0-9a-f]{64}");
    private static final Pattern MISE_VERSION = Pattern.compile("\\d{4}\\.\\d{1,2}\\.\\d+");
    private static final String LOCAL_ACTION = "./";
    private static final String DOCKER_ACTION = "docker://";
    private static final String MISE_ACTION = "jdx/mise-action@";

    @Test
    void pinsEveryActionToAFullCommit() {
        List<String> uses = workflows().flatMap(PinnedActionsTest::usesOf).toList();

        assertThat(uses).isNotEmpty();
        assertThat(uses).filteredOn(action -> !isPinned(action)).isEmpty();
    }

    @Test
    void keepsTheTagBesideEveryPinnedAction() {
        List<String> lines = workflowFiles().flatMap(PinnedActionsTest::linesOf).filter(line -> USES_LINE.matcher(line).matches()).toList();

        assertThat(lines).isNotEmpty();
        assertThat(lines).filteredOn(line -> !TAG_BESIDE.matcher(line).matches()).isEmpty();
    }

    @Test
    void catchesAnUnpinnedActionWrittenInAnyFormYamlAllows() {
        JsonNode workflow = YAML.readTree("""
                jobs:
                  build:
                    steps:
                      - {uses: actions/checkout@v4}
                      - uses: "actions/cache@v4"
                      - name: Set up
                        uses: 'jdx/mise-action@main'
                      - uses: actions/setup-java@0123456789abcdef0123456789abcdef01234567 # v5.0.0
                """);

        assertThat(usesOf(workflow).filter(action -> !isPinned(action)))
                .containsExactly("actions/checkout@v4", "actions/cache@v4", "jdx/mise-action@main");
    }

    @Test
    void pinsEveryServiceAndContainerImageByDigest() {
        List<String> images = workflows().flatMap(PinnedActionsTest::imagesOf).toList();

        assertThat(images).isNotEmpty();
        assertThat(images).filteredOn(image -> !PINNED_IMAGE.matcher(image).matches()).isEmpty();
    }

    @Test
    void namesTheMiseVersionEveryMiseActionStepInstalls() {
        List<JsonNode> miseSteps = workflows().flatMap(PinnedActionsTest::stepsOf).filter(step -> step.path("uses").asString("").startsWith(MISE_ACTION))
                .toList();

        assertThat(miseSteps).isNotEmpty();
        assertThat(miseSteps).allSatisfy(step -> assertThat(step.path("with").path("version").asString("")).matches(MISE_VERSION));
    }

    @Test
    void letsRenovateKeepTheActionsAndImageDigestsAndTheMiseVersionCurrent() throws IOException {
        JsonNode renovate = new JsonMapper().readTree(Files.readString(REPOSITORY.resolve("renovate.json")));

        assertThat(renovate.path("packageRules").valueStream())
                .anyMatch(rule -> rule.path("matchManagers").valueStream().anyMatch(manager -> manager.asString().equals("github-actions"))
                        && rule.path("pinDigests").asBoolean(false));
        assertThat(renovate.path("customManagers").valueStream())
                .anyMatch(manager -> manager.path("packageNameTemplate").asString("").equals("jdx/mise")
                        && manager.path("managerFilePatterns").valueStream().anyMatch(pattern -> pattern.asString().contains(".github")));
    }

    private static boolean isPinned(String action) {
        if (action.startsWith(LOCAL_ACTION)) {
            return true;
        }
        return action.startsWith(DOCKER_ACTION) ? PINNED_IMAGE.matcher(action).matches() : PINNED_ACTION.matcher(action).matches();
    }

    private static Stream<JsonNode> workflows() {
        return workflowFiles().map(PinnedActionsTest::parsed);
    }

    private static Stream<Path> workflowFiles() {
        try (Stream<Path> files = Files.list(WORKFLOWS)) {
            return files.filter(file -> file.toString().endsWith(".yml") || file.toString().endsWith(".yaml")).sorted().toList().stream();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static JsonNode parsed(Path workflow) {
        try {
            return YAML.readTree(Files.readString(workflow));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Stream<String> usesOf(JsonNode workflow) {
        return stepsOf(workflow).map(step -> step.path("uses")).filter(JsonNode::isString).map(JsonNode::asString);
    }

    private static Stream<JsonNode> stepsOf(JsonNode workflow) {
        return workflow.path("jobs").valueStream().flatMap(job -> job.path("steps").valueStream());
    }

    private static Stream<String> imagesOf(JsonNode workflow) {
        return workflow.path("jobs").valueStream().flatMap(job -> Stream.concat(
                job.path("services").valueStream().map(service -> service.path("image")),
                Stream.of(job.path("container")).map(container -> container.isString() ? container : container.path("image"))))
                .filter(JsonNode::isString).map(JsonNode::asString);
    }

    private static Stream<String> linesOf(Path workflow) {
        try {
            return Files.readAllLines(workflow).stream();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
