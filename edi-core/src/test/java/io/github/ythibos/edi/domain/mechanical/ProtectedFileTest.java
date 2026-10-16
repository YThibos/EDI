package io.github.ythibos.edi.domain.mechanical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProtectedFileTest {

    @ParameterizedTest
    @CsvSource({
        "docs/decisions/ADR-007-six-ports.md, DECISION_RECORD",
        "docs/adr/0003-use-postgres.md, DECISION_RECORD",
        "src/main/resources/db/migration/V2__workers.sql, MIGRATION",
        "schema/V12_1__add_index.sql, MIGRATION",
        "src/main/java/app/SecurityConfig.java, SECURITY_CONFIGURATION",
        "edi-core/src/test/java/io/LampTest.java, TEST_EXPECTATION",
        "edi-cli/src/main/java/NativeSmokeIT.java, TEST_EXPECTATION",
        "console/src/app/lamp.spec.ts, TEST_EXPECTATION",
        "tests/golden/report.txt, TEST_EXPECTATION"})
    void recognisesWhatAMechanicalTaskNeverEdits(String file, ProtectedFile kind) {
        assertThat(ProtectedFile.of(file)).contains(kind);
    }

    @ParameterizedTest
    @ValueSource(strings = {"README.md", "src/main/java/app/Lamp.java", "src/main/java/app/Contest.java", "src/main/java/app/Audit.java", "docs/guide.md"})
    void leavesOrdinaryFilesToTheMechanicalTask(String file) {
        assertThat(ProtectedFile.of(file)).isEmpty();
    }

    @Test
    void namesTheChangedFilesBeyondTheExpectedChange() {
        ExpectedChange change = new ExpectedChange("Rename Lamp to Lantern.", List.of("src/Lamp.java", "README.md"));

        assertThat(change.filesBeyond(Set.of("README.md", "src/Lamp.java"))).isEmpty();
        assertThat(change.filesBeyond(Set.of("src/Lamp.java", "src/Horn.java", "pom.xml"))).containsExactly("pom.xml", "src/Horn.java");
    }
}
