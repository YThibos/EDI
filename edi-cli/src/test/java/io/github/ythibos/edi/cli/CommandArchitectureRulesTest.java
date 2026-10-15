package io.github.ythibos.edi.cli;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.github.ythibos.edi.architecture.NameAMergeOperation;

@AnalyzeClasses(packages = "io.github.ythibos.edi.cli", importOptions = ImportOption.DoNotIncludeTests.class)
class CommandArchitectureRulesTest {

    @ArchTest
    static final ArchRule noCommandNamesAMergeOperation = noClasses()
            .should(new NameAMergeOperation())
            .because("EDI has no merge capability of any kind (I-03, AT-03a)");
}
