package io.github.ythibos.edi.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class AnchorIndexTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Path ANCHOR = REPOSITORY.resolve("claude/primary/CLAUDE.md");
    private static final Path SKILLS = REPOSITORY.resolve("claude/skills");
    private static final Path CATALOGUE = REPOSITORY.resolve("edi-core/src/test/java/io/github/ythibos/edi/invariant");
    private static final int ANCHOR_INDEX_BUDGET_BYTES = 7_000;
    private static final Pattern ANCHOR_RULE = Pattern.compile("^- (I-\\d{2})\\*? (.+)$", Pattern.MULTILINE);
    private static final Pattern CATALOGUE_CLASS = Pattern.compile("@Nested\\s+@DisplayName\\(\"(I-\\d{2}) ([^\"]+)\"\\)");

    @Test
    void theAnchorStaysUnderItsIndexBudget() throws IOException {
        assertThat(Files.readString(ANCHOR).getBytes(StandardCharsets.UTF_8)).as("bytes of the primary's anchor").hasSizeLessThan(ANCHOR_INDEX_BUDGET_BYTES);
    }

    @Test
    void everyRuleTheAnchorNamesHasANestedClassInTheCatalogue() throws IOException {
        assertThat(anchorRules()).isNotEmpty();
        assertThat(anchorRules().keySet()).isSubsetOf(catalogueWordings().keySet());
    }

    @Test
    void everyInvariantOfTheCatalogueIsNamedInTheAnchor() throws IOException {
        assertThat(anchorRules().keySet()).containsAll(catalogueWordings().keySet());
    }

    @Test
    void everyAnchorRuleIsWordedAsItsCatalogueClass() throws IOException {
        Map<String, Set<String>> catalogue = catalogueWordings();

        anchorRules().forEach((number, wording) -> assertThat(catalogue.get(number)).as("the catalogue's wordings of " + number).contains(wording));
    }

    @Test
    void noSkillRestatesTheTextOfAnAnchorRule() throws IOException {
        Set<String> rules = anchorRules().values().stream().map(AnchorIndexTest::plainWords).collect(Collectors.toSet());

        for (Path skill : skillFiles()) {
            String words = plainWords(Files.readString(skill));
            assertThat(rules).as("anchor rules restated in " + REPOSITORY.relativize(skill)).noneMatch(words::contains);
        }
    }

    private static Map<String, String> anchorRules() throws IOException {
        Map<String, String> rules = new TreeMap<>();
        Matcher rule = ANCHOR_RULE.matcher(Files.readString(ANCHOR));
        while (rule.find()) {
            rules.put(rule.group(1), rule.group(2));
        }
        return rules;
    }

    private static Map<String, Set<String>> catalogueWordings() throws IOException {
        Map<String, Set<String>> wordings = new TreeMap<>();
        for (Path testClass : catalogueClasses()) {
            Matcher invariant = CATALOGUE_CLASS.matcher(Files.readString(testClass));
            while (invariant.find()) {
                wordings.computeIfAbsent(invariant.group(1), number -> new TreeSet<>()).add(invariant.group(2));
            }
        }
        return wordings;
    }

    private static List<Path> catalogueClasses() throws IOException {
        try (Stream<Path> files = Files.list(CATALOGUE)) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        }
    }

    private static List<Path> skillFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SKILLS)) {
            return files.filter(file -> file.getFileName().toString().equals("SKILL.md")).toList();
        }
    }

    private static String plainWords(String text) {
        return " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip() + " ";
    }
}
