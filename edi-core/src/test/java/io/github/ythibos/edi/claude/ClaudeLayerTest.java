package io.github.ythibos.edi.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

class ClaudeLayerTest {

    private static final Path CLAUDE_LAYER = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize().resolve("claude");
    private static final Pattern SLASH_COMMAND = Pattern.compile("(?<![\\w/.~$-])/([a-z][a-z-]*)(?![\\w/.-])");
    private static final String FRONTMATTER_FENCE = "---\n";

    @Test
    void everyCommandTheCaptainIsToldToTypeIsTheSkillOfItsName() throws IOException {
        assertThat(commandsTheCaptainIsToldToType()).as("commands the captain is told to type")
                .isNotEmpty()
                .isSubsetOf(skillsTheCaptainCanInvoke());
    }

    @Test
    void everySkillIsNamedAfterTheDirectoryTheInstallLinks() throws IOException {
        assertThat(skills()).isNotEmpty().allSatisfy(skill -> assertThat(skill.name()).isEqualTo(skill.directory()));
    }

    private static Set<String> commandsTheCaptainIsToldToType() throws IOException {
        Stream<String> triggers = skills().stream().map(Skill::trigger);
        return Stream.concat(Stream.of(Files.readString(CLAUDE_LAYER.resolve("primary/CLAUDE.md"))), triggers)
                .flatMap(text -> SLASH_COMMAND.matcher(text).results().map(command -> command.group(1)))
                .collect(Collectors.toSet());
    }

    private static Set<String> skillsTheCaptainCanInvoke() throws IOException {
        return skills().stream().filter(Skill::isInvocableByTheCaptain).map(Skill::name).collect(Collectors.toSet());
    }

    private static List<Skill> skills() throws IOException {
        try (Stream<Path> directories = Files.list(CLAUDE_LAYER.resolve("skills"))) {
            return directories.map(Skill::in).toList();
        }
    }

    private record Skill(String directory, JsonNode frontmatter) {

        static Skill in(Path directory) {
            return new Skill(directory.getFileName().toString(), frontmatterOf(directory.resolve("SKILL.md")));
        }

        String name() {
            return frontmatter.path("name").asString();
        }

        String trigger() {
            return frontmatter.path("description").asString();
        }

        boolean isInvocableByTheCaptain() {
            return frontmatter.path("user-invocable").asBoolean(true);
        }

        private static JsonNode frontmatterOf(Path skill) {
            try {
                String text = Files.readString(skill);
                int end = text.indexOf("\n" + FRONTMATTER_FENCE, FRONTMATTER_FENCE.length());
                return new YAMLMapper().readTree(text.substring(FRONTMATTER_FENCE.length(), end));
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }
}
