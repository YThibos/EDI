package io.github.ythibos.edi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.testing.RuleCatalogue;
import io.github.ythibos.edi.testing.RuleCatalogue.Entry;

@AnalyzeClasses(packages = "io.github.ythibos.edi", importOptions = {ImportOption.DoNotIncludeTests.class, HandWrittenClasses.class})
class RuleCatalogueTest {

    private static final Set<String> PLACES_OUTSIDE_THE_CORE = Set.of("Edi", "HookCommand", "WaitCommand", "PublishCheckCommand", "commit-msg");

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
                .as("where %s lives; the catalogue row is: %s", rule.id(), expectedRow(core, catalogue, rule))
                .isNotEmpty()
                .containsExactlyElementsOf(placesOf(core, catalogue, rule)));
    }

    @ArchTest
    static void refusesOnlyUnderARuleOfTheCatalogue(JavaClasses core) {
        assertThat(core.get(Refusal.class).getConstructors()).allSatisfy(constructor ->
                assertThat(constructor.getRawParameterTypes()).extracting(JavaClass::getName).contains(Rule.class.getName()));
    }

    private static Map<String, Entry> catalogue() {
        return RuleCatalogue.ofTheRepository().entries();
    }

    private static Entry entryOf(Map<String, Entry> catalogue, Rule rule) {
        return catalogue.getOrDefault(rule.id(), new Entry(List.of(), ""));
    }

    private static List<String> placesOf(JavaClasses core, Map<String, Entry> catalogue, Rule rule) {
        Set<String> places = new TreeSet<>(coreClassesFiring(core, rule));
        places.addAll(placesOutsideTheCore(entryOf(catalogue, rule)));
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

    private static List<String> placesOutsideTheCore(Entry entry) {
        return entry.places().stream().filter(PLACES_OUTSIDE_THE_CORE::contains).toList();
    }

    private static String expectedRow(JavaClasses core, Map<String, Entry> catalogue, Rule rule) {
        return "| `" + rule.id() + "` | " + placesOf(core, catalogue, rule).stream().map(place -> "`" + place + "`").collect(Collectors.joining(", "))
                + " | " + rule.protects() + " |";
    }
}
