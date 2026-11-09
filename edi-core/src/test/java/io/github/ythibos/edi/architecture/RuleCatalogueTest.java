package io.github.ythibos.edi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

@AnalyzeClasses(packages = "io.github.ythibos.edi", importOptions = {ImportOption.DoNotIncludeTests.class, HandWrittenClasses.class})
class RuleCatalogueTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Path CATALOGUE = REPOSITORY.resolve("docs/rules.md");
    private static final Path COMMAND_SOURCES = REPOSITORY.resolve("edi-cli/src/main/java");
    private static final Path GIT_HOOKS = REPOSITORY.resolve("edi-core/src/main/resources/git-hooks");
    private static final Pattern CATALOGUE_ROW = Pattern.compile("^\\| `([^`]+)` \\| ([^|]*) \\| ([^|]*) \\|$");
    private static final Pattern PLACE = Pattern.compile("`([^`]+)`");
    private static final Pattern COMMAND_RULE = Pattern.compile("static final String ([A-Z_]+) = \"([^\"]+)\";");
    private static final Pattern HOOK_RULE = Pattern.compile("refuse ([a-z]+\\.[a-z0-9-]+) ");

    @ArchTest
    static void listsEveryRuleOfTheCodeOnceInItsOrderAndNoOther(JavaClasses core) {
        assertThat(catalogue().keySet()).containsExactlyElementsOf(Arrays.stream(Rule.values()).map(Rule::id).toList());
    }

    @ArchTest
    static void saysWhatEachRuleProtectsAsTheCodeDoes(JavaClasses core) {
        Map<String, Entry> catalogue = catalogue();

        assertThat(Rule.values()).allSatisfy(rule -> assertThat(entryOf(catalogue, rule).protects()).as(rule.id()).isEqualTo(rule.protects()));
    }

    @ArchTest
    static void namesWhereEachRuleLivesAndEveryRuleLivesSomewhere(JavaClasses core) {
        Map<String, Entry> catalogue = catalogue();

        assertThat(Rule.values()).allSatisfy(rule -> assertThat(entryOf(catalogue, rule).places())
                .as("where %s lives; the catalogue row is: %s", rule.id(), expectedRow(core, rule))
                .isNotEmpty()
                .containsExactlyElementsOf(placesOf(core, rule)));
    }

    @ArchTest
    static void firesOnlyRulesOfTheCatalogueOutsideTheCore(JavaClasses core) {
        Set<String> ids = Arrays.stream(Rule.values()).map(Rule::id).collect(Collectors.toSet());

        assertThat(commandRules().toList()).isNotEmpty()
                .allSatisfy(named -> assertThat(named.id()).as(named.file() + " names " + named.constant()).isEqualTo(Rule.valueOf(named.constant()).id()));
        assertThat(hookRules().toList()).isNotEmpty().allSatisfy(named -> assertThat(ids).as(named.file().toString()).contains(named.id()));
    }

    @ArchTest
    static void refusesOnlyUnderARuleOfTheCatalogue(JavaClasses core) {
        assertThat(core.get(Refusal.class).getConstructors()).allSatisfy(constructor ->
                assertThat(constructor.getRawParameterTypes()).extracting(JavaClass::getName).contains(Rule.class.getName()));
    }

    private static Map<String, Entry> catalogue() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String line : linesOf(CATALOGUE)) {
            Matcher row = CATALOGUE_ROW.matcher(line);
            if (row.matches()) {
                entries.put(row.group(1), new Entry(placesIn(row.group(2)), row.group(3)));
            }
        }
        return entries;
    }

    private static Entry entryOf(Map<String, Entry> catalogue, Rule rule) {
        return catalogue.getOrDefault(rule.id(), new Entry(List.of(), ""));
    }

    private static List<String> placesIn(String cell) {
        return PLACE.matcher(cell).results().map(found -> found.group(1)).toList();
    }

    private static List<String> placesOf(JavaClasses core, Rule rule) {
        Set<String> places = new TreeSet<>();
        places.addAll(coreClassesFiring(core, rule));
        places.addAll(filesNaming(rule.id()));
        return List.copyOf(places);
    }

    private static Set<String> coreClassesFiring(JavaClasses core, Rule rule) {
        return core.stream()
                .filter(javaClass -> javaClass.getFieldAccessesFromSelf().stream().anyMatch(access -> readsTheRule(access, rule)))
                .map(RuleCatalogueTest::outermostNameOf)
                .filter(name -> !name.equals(Rule.class.getSimpleName()))
                .collect(Collectors.toSet());
    }

    private static boolean readsTheRule(JavaFieldAccess access, Rule rule) {
        return access.getTargetOwner().isEquivalentTo(Rule.class) && access.getName().equals(rule.name());
    }

    private static String outermostNameOf(JavaClass javaClass) {
        JavaClass outermost = javaClass;
        while (outermost.getEnclosingClass().isPresent()) {
            outermost = outermost.getEnclosingClass().get();
        }
        return outermost.getSimpleName();
    }

    private static Set<String> filesNaming(String id) {
        return Stream.concat(commandRules(), hookRules()).filter(named -> named.id().equals(id))
                .map(named -> named.file().getFileName().toString().replaceFirst("\\.java$", ""))
                .collect(Collectors.toSet());
    }

    private static Stream<NamedRule> commandRules() {
        Set<String> constants = Arrays.stream(Rule.values()).map(Rule::name).collect(Collectors.toSet());
        return filesUnder(COMMAND_SOURCES).flatMap(file -> linesOf(file).stream()
                .flatMap(line -> COMMAND_RULE.matcher(line).results())
                .filter(found -> constants.contains(found.group(1)))
                .map(found -> new NamedRule(file, found.group(1), found.group(2))));
    }

    private static Stream<NamedRule> hookRules() {
        return filesUnder(GIT_HOOKS).flatMap(file -> linesOf(file).stream()
                .flatMap(line -> HOOK_RULE.matcher(line).results())
                .map(found -> new NamedRule(file, null, found.group(1))));
    }

    private static Stream<Path> filesUnder(Path sources) {
        try (Stream<Path> files = Files.walk(sources)) {
            return files.filter(Files::isRegularFile).toList().stream();
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

    private static String expectedRow(JavaClasses core, Rule rule) {
        return "| `" + rule.id() + "` | " + placesOf(core, rule).stream().map(place -> "`" + place + "`").collect(Collectors.joining(", ")) + " | "
                + rule.protects() + " |";
    }

    private record Entry(Collection<String> places, String protects) {
    }

    private record NamedRule(Path file, String constant, String id) {
    }
}
