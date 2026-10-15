package io.github.ythibos.edi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class PortInventoryTest {

    private static final String PORTS = "io.github.ythibos.edi.application.port";

    @Test
    @DisplayName("AT-45 the core declares exactly the six ports of its architecture")
    void theCoreDeclaresExactlyTheSixPortsOfItsArchitecture() {
        var ports = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(new HandWrittenClasses())
                .importPackages(PORTS)
                .stream()
                .filter(JavaClass::isTopLevelClass)
                .toList();

        assertThat(ports).allSatisfy(port -> assertThat(port.isInterface()).as("%s is an interface", port.getName()).isTrue());
        assertThat(ports).extracting(JavaClass::getSimpleName).containsExactlyInAnyOrder(
                "Forge", "Tracker", "WorkerRuntime", "Pipeline", "ResearchBoardHost", "Clock");
    }
}
