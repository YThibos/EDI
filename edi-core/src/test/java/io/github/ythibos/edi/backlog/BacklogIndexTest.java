package io.github.ythibos.edi.backlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class BacklogIndexTest {

    private static final Path BACKLOG = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize().resolve("docs/backlog");
    private static final Pattern ITEM_FILE = Pattern.compile("EDI-\\d+\\.md");
    private static final Pattern INDEXED_ITEM = Pattern.compile("^\\| [^|]+ \\| \\[(EDI-\\d+)]\\((EDI-\\d+\\.md)\\) \\|");
    private static final Pattern STATUS = Pattern.compile("^\\| Status \\| \\S.* \\|$");
    private static final Pattern ISSUE = Pattern.compile("^\\| Issue \\| (-|\\[#\\d+]\\(https://github\\.com/[^)]+/issues/\\d+\\)) \\|$");

    @Test
    void indexesEveryItemFileExactlyOnceAndOnlyFilesThatExist() {
        List<String> indexed = indexedFiles();

        assertThat(indexed).as("items indexed more than once").doesNotHaveDuplicates();
        assertThat(indexed).as("index rows and item files").containsExactlyInAnyOrderElementsOf(itemFiles());
    }

    @Test
    void keepsStatusAndIssueOutOfTheIndex() {
        List<String> headers = indexLines().stream().filter(line -> line.startsWith("| Order |")).toList();

        assertThat(headers).isNotEmpty().allSatisfy(header -> assertThat(header).doesNotContain("| Status |").doesNotContain("| Issue |"));
    }

    @Test
    void givesEveryItemFileItsStatusAndItsIssueInItsHeader() {
        assertThat(itemFiles()).allSatisfy(file -> {
            List<String> lines = linesOf(BACKLOG.resolve(file));
            assertThat(lines).as("status of %s", file).filteredOn(line -> STATUS.matcher(line).matches()).hasSize(1);
            assertThat(lines).as("issue of %s", file).filteredOn(line -> ISSUE.matcher(line).matches()).hasSize(1);
        });
    }

    private static List<String> indexedFiles() {
        List<String> files = new ArrayList<>();
        for (String line : indexLines()) {
            Matcher row = INDEXED_ITEM.matcher(line);
            if (row.find()) {
                assertThat(row.group(2)).as("link of %s", row.group(1)).isEqualTo(row.group(1) + ".md");
                files.add(row.group(2));
            }
        }
        return files;
    }

    private static List<String> indexLines() {
        List<String> lines = linesOf(BACKLOG.resolve("README.md"));
        int start = lines.indexOf("## The index");
        assertThat(start).as("the index section").isNotNegative();
        return lines.subList(start, lines.size());
    }

    private static List<String> itemFiles() {
        try (Stream<Path> files = Files.list(BACKLOG)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> ITEM_FILE.matcher(name).matches()).sorted().toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> linesOf(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
