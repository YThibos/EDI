package io.github.ythibos.edi.decisions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DecisionLogTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Path DECISION_LOG = REPOSITORY.resolve("docs/decisions");
    private static final String RULES = "README.md";
    private static final Pattern TOPIC_SLUG = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*\\.md");
    private static final Pattern KIND = Pattern.compile("\\*\\*Kind:\\*\\* (Architecture|Design)");
    private static final List<String> SECTIONS = List.of(
            "Context", "Decision", "Why", "Alternatives rejected", "Consequences", "When to revisit", "How we got here");
    private static final Pattern NUMBERED_RECORD = Pattern.compile("\\bADR-\\d+");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)#\\s]+\\.md)(#[^)]*)?\\)");
    private static final Pattern RECORD_PATH = Pattern.compile("docs/decisions/([a-z0-9-]+\\.md)");
    private static final Set<String> UNTRACKED_DIRECTORIES = Set.of(
            ".git", "target", "node_modules", ".angular", "dist", "node", "personal", ".idea", ".vscode");
    private static final Path CAPTURED_FIXTURES = Path.of("src", "test", "resources");

    @Test
    void everyRecordIsNamedByItsTopic() {
        assertThat(records()).isNotEmpty()
                .allSatisfy(record -> assertThat(record.getFileName().toString()).as("record file name").matches(TOPIC_SLUG));
    }

    @Test
    void everyRecordStatesItsDecisionKindAndEverySectionInOrder() {
        assertThat(records()).allSatisfy(record -> {
            List<String> lines = linesOf(record);
            assertThat(lines.getFirst()).as("title of %s", record.getFileName()).startsWith("# ");
            assertThat(lines).as("kind of %s", record.getFileName()).anySatisfy(line -> assertThat(line).matches(KIND));
            assertThat(sectionsOf(lines)).as("sections of %s", record.getFileName()).isEqualTo(SECTIONS);
        });
    }

    @Test
    void everyLinkToARecordResolves() {
        assertThat(repositoryTexts()).allSatisfy((file, text) ->
                assertThat(recordsCitedBy(file, text)).as("records cited by %s", REPOSITORY.relativize(file)).allSatisfy(cited ->
                        assertThat(cited).exists()));
    }

    @Test
    void nothingCitesARecordByNumber() {
        assertThat(repositoryTexts()).allSatisfy((file, text) ->
                assertThat(NUMBERED_RECORD.matcher(text).find()).as("a numbered record cited in %s", REPOSITORY.relativize(file)).isFalse());
    }

    private static List<Path> records() {
        try (Stream<Path> files = Files.list(DECISION_LOG)) {
            return files.filter(file -> !file.getFileName().toString().equals(RULES)).sorted().toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> sectionsOf(List<String> lines) {
        return lines.stream().filter(line -> line.startsWith("## ")).map(line -> line.substring(3)).toList();
    }

    private static Map<Path, String> repositoryTexts() {
        Map<Path, String> texts = new TreeMap<>();
        try {
            Files.walkFileTree(REPOSITORY, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    return isLeftOut(directory) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    textIfStillThere(file).ifPresent(text -> texts.put(file, text));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return texts;
    }

    private static Optional<String> textIfStillThere(Path file) {
        try {
            return Optional.of(textOf(file));
        } catch (UncheckedIOException unreadable) {
            if (unreadable.getCause() instanceof NoSuchFileException) {
                return Optional.empty();
            }
            throw unreadable;
        }
    }

    private static boolean isLeftOut(Path directory) {
        return UNTRACKED_DIRECTORIES.contains(directory.getFileName().toString()) || directory.endsWith(CAPTURED_FIXTURES);
    }

    private static Stream<Path> recordsCitedBy(Path file, String text) {
        Stream<Path> byPath = RECORD_PATH.matcher(text).results().map(cited -> DECISION_LOG.resolve(cited.group(1)));
        Stream<Path> byLink = file.toString().endsWith(".md")
                ? MARKDOWN_LINK.matcher(text).results()
                        .map(link -> file.getParent().resolve(link.group(1)).normalize())
                        .filter(target -> DECISION_LOG.equals(target.getParent()))
                : Stream.empty();
        return Stream.concat(byPath, byLink);
    }

    private static List<String> linesOf(Path file) {
        return textOf(file).lines().toList();
    }

    private static String textOf(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
