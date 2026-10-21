package io.github.ythibos.edi.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.github.ythibos.edi.adapter.process.Program;

@AnalyzeClasses(packages = "io.github.ythibos.edi", importOptions = {ImportOption.DoNotIncludeTests.class, HandWrittenClasses.class})
class ArchitectureRulesTest {

    private static final String DOMAIN = "io.github.ythibos.edi.domain..";
    private static final String APPLICATION = "io.github.ythibos.edi.application..";
    private static final String PORTS = "io.github.ythibos.edi.application.port..";
    private static final String ADAPTERS = "io.github.ythibos.edi.adapter..";
    private static final String WEB = "io.github.ythibos.edi.web..";
    private static final String PROCESS_ADAPTER = "io.github.ythibos.edi.adapter.process..";
    private static final String CLOCK_ADAPTER = "io.github.ythibos.edi.adapter.clock..";

    private static final Map<String, String> OWNING_ADAPTER_OF_PROGRAM = Map.ofEntries(
            Map.entry("GIT", "io.github.ythibos.edi.adapter.git.."),
            Map.entry("GITLEAKS", "io.github.ythibos.edi.adapter.gitleaks.."),
            Map.entry("TMUX", "io.github.ythibos.edi.adapter.runtime.."),
            Map.entry("CLAUDE_CODE", "io.github.ythibos.edi.adapter.runtime.."),
            Map.entry("GLAB", "io.github.ythibos.edi.adapter.forge.."),
            Map.entry("GH", "io.github.ythibos.edi.adapter.forge.."),
            Map.entry("NO_MISTAKES", "io.github.ythibos.edi.adapter.pipeline.."),
            Map.entry("SHELL", "io.github.ythibos.edi.adapter.build.."),
            Map.entry("LAVISH", "io.github.ythibos.edi.adapter.board.."),
            Map.entry("CMD", "io.github.ythibos.edi.adapter.browser.."),
            Map.entry("XDG_OPEN", "io.github.ythibos.edi.adapter.browser.."),
            Map.entry("POWERSHELL", "io.github.ythibos.edi.adapter.windows.."));

    @ArchTest
    static final ArchRule theDomainDependsOnlyOnItselfAndTheCoreJavaLibrary = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, "java.lang..", "java.util..", "java.time..", "java.math..")
            .because("the domain holds EDI's safety rules and must test in milliseconds without Spring, input or output");

    @ArchTest
    static final ArchRule theDomainTouchesNoProcessSystemOrReflection = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().belongToAnyOf(
                    ProcessBuilder.class, Process.class, ProcessHandle.class, Runtime.class, System.class, Thread.class)
            .orShould().dependOnClassesThat().resideInAPackage("java.lang.reflect..");

    @ArchTest
    static final ArchRule onlyTheProcessAdapterStartsPrograms = noClasses()
            .that().resideOutsideOfPackage(PROCESS_ADAPTER)
            .should().dependOnClassesThat().belongToAnyOf(ProcessBuilder.class, Process.class, ProcessHandle.class)
            .orShould().callMethodWhere(target(name("exec")).and(target(owner(type(Runtime.class)))));

    @ArchTest
    static final ArchRule everyTimeReadGoesThroughTheClock = noClasses()
            .that().resideOutsideOfPackage(CLOCK_ADAPTER)
            .should().callMethodWhere(target(name("now")).and(target(owner(resideInAPackage("java.time..")))))
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callMethod(System.class, "nanoTime")
            .orShould().callConstructor(Date.class)
            .because("tests control time through the Clock port");

    @ArchTest
    static final ArchRule noClassNamesAMergeOperation = noClasses()
            .should(new NameAMergeOperation())
            .because("EDI has no merge capability of any kind (I-03, AT-03a)");

    @ArchTest
    static final ArchRule nothingLoadsClassesAtRuntime = noClasses()
            .should().callMethodWhere(target(name("forName")).and(target(owner(type(Class.class)))))
            .orShould().callMethodWhere(target(name("loadClass")).and(target(owner(type(ClassLoader.class)))))
            .because("a native image fixes its classes at build time");

    @ArchTest
    static final ArchRule noClassChoosesMachineDifferencesAtBuildTime = noClasses()
            .should().beAnnotatedWith(Profile.class)
            .orShould().beAnnotatedWith(ConditionalOnProperty.class)
            .because("a native image evaluates these once at build time; the machine profile decides at runtime");

    @ArchTest
    static final ArchRule noBeanMethodChoosesMachineDifferencesAtBuildTime = noMethods()
            .should().beAnnotatedWith(Profile.class)
            .orShould().beAnnotatedWith(ConditionalOnProperty.class)
            .because("a native image evaluates these once at build time; the machine profile decides at runtime");

    @ArchTest
    static final ArchRule everyApplicationInterfaceIsAPort = classes()
            .that().resideInAPackage(APPLICATION).and().areInterfaces()
            .should().resideInAPackage(PORTS)
            .because("the port inventory is fixed and counted (AT-45)");

    @ArchTest
    static final ArchRule onlyAdaptersImplementPorts = classes()
            .that().implement(resideInAPackage(PORTS))
            .should().resideInAPackage(ADAPTERS)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule adaptersReachTheApplicationOnlyThroughPorts = noClasses()
            .that().resideInAPackage(ADAPTERS)
            .should().dependOnClassesThat(resideInAPackage(APPLICATION).and(not(resideInAPackage(PORTS))));

    @ArchTest
    static final ArchRule nothingDependsOnTheWebLayer = noClasses()
            .that().resideOutsideOfPackage(WEB)
            .should().dependOnClassesThat().resideInAPackage(WEB);

    @ArchTest
    static void everyProgramRunsOnlyFromTheAdapterThatOwnsIt(JavaClasses core) {
        for (Program program : Program.values()) {
            String owner = OWNING_ADAPTER_OF_PROGRAM.get(program.name());
            assertThat(owner).as("the adapter that owns %s", program).isNotNull();
            noClasses()
                    .that().resideOutsideOfPackage(owner).and().doNotBelongToAnyOf(Program.class)
                    .should().accessField(Program.class, program.name())
                    .because("only adapter.runtime starts tmux or Claude Code and only adapter.forge runs glab or gh")
                    .check(core);
        }
    }
}
