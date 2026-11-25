package io.github.ythibos.edi.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class AnchorIndexTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Path ANCHOR = REPOSITORY.resolve("claude/primary/CLAUDE.md");
    private static final Path SKILLS = REPOSITORY.resolve("claude/skills");
    private static final String CATALOGUE = "io.github.ythibos.edi.invariant";
    private static final int ANCHOR_INDEX_BUDGET_BYTES = 7_000;
    private static final int WORDS_IN_A_RESTATEMENT = 6;
    private static final Pattern ANCHOR_RULE = Pattern.compile("^- (I-\\d{2})\\*? (.+)$", Pattern.MULTILINE);
    private static final Pattern INVARIANT_NAME = Pattern.compile("(I-\\d{2}) (.+)");

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
    void everyAnchorRuleIsWordedAsItsOneCatalogueClass() throws IOException {
        Map<String, List<String>> catalogue = catalogueWordings();

        anchorRules().forEach((number, wording) -> assertThat(catalogue.get(number)).as("the catalogue's wordings of " + number).containsExactly(wording));
    }

    @Test
    void noSkillRestatesTheTextOfAnAnchorRule() throws IOException {
        Collection<String> rules = anchorRules().values();

        for (Path skill : skillFiles()) {
            String text = Files.readString(skill);
            assertThat(rules).as("anchor rules restated in " + REPOSITORY.relativize(skill)).noneMatch(rule -> restates(text, rule));
        }
    }

    @Test
    void aSkillRestatesARuleWithSixWordsInARowOfItsWordingAndNotByCitingItsNumber() {
        String neverPressAKey = "Never press a key into a dialog; Escape only, if the captain asks";

        assertThat(restates("- Never press a key into a limit, spend-cap or continue dialog; Escape only, and only when the captain asks.", neverPressAKey)).isTrue();
        assertThat(restates("- A limit, spend-cap or continue dialog is I-28's.", neverPressAKey)).isFalse();
    }

    private static Map<String, String> anchorRules() throws IOException {
        Map<String, String> rules = new TreeMap<>();
        Matcher rule = ANCHOR_RULE.matcher(Files.readString(ANCHOR));
        while (rule.find()) {
            rules.put(rule.group(1), rule.group(2));
        }
        return rules;
    }

    private static Map<String, List<String>> catalogueWordings() {
        Map<String, List<String>> wordings = new TreeMap<>();
        invariantClassNames().map(INVARIANT_NAME::matcher).filter(Matcher::matches)
                .forEach(invariant -> wordings.computeIfAbsent(invariant.group(1), number -> new ArrayList<>()).add(invariant.group(2)));
        return wordings;
    }

    private static Stream<String> invariantClassNames() {
        return new ClassFileImporter().importPackages(CATALOGUE).stream()
                .filter(testClass -> testClass.isAnnotatedWith(Nested.class) && testClass.isAnnotatedWith(DisplayName.class))
                .map(AnchorIndexTest::displayName);
    }

    private static String displayName(JavaClass testClass) {
        return testClass.getAnnotationOfType(DisplayName.class).value();
    }

    private static List<Path> skillFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SKILLS)) {
            return files.filter(file -> file.getFileName().toString().equals("SKILL.md")).toList();
        }
    }

    private static boolean restates(String text, String rule) {
        List<String> textWords = plainWords(text);
        return runsOfRestatementLength(plainWords(rule)).anyMatch(run -> Collections.indexOfSubList(textWords, run) >= 0);
    }

    private static List<String> plainWords(String text) {
        String withoutApostrophes = text.toLowerCase(Locale.ROOT).replaceAll("['’]", "");
        return Arrays.stream(withoutApostrophes.split("[^a-z0-9]+")).filter(word -> !word.isEmpty()).toList();
    }

    private static Stream<List<String>> runsOfRestatementLength(List<String> words) {
        return IntStream.rangeClosed(0, words.size() - WORDS_IN_A_RESTATEMENT).mapToObj(start -> words.subList(start, start + WORDS_IN_A_RESTATEMENT));
    }
}
