package io.github.ythibos.edi.adapter.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.DispatchCategory;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.testing.ExampleHome;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class DispatchRulesFileTest {

    @TempDir
    private Path home;

    @Test
    void carriesOldReasonsIntoTheCategoriesAndOverridesTheyBelongTo() throws IOException {
        String example = ExampleHome.file("config/dispatch-rules.json");
        DispatchRulesFile rules = new DispatchRulesFile(new EdiHome(home), new JsonMapper());

        String carried = rules.withCarriedReasons(example, Map.of(CategoryId.RESEARCH, List.of("Plans decide rounds.", "Findings aim later work.")),
                Map.of(new ProjectName("EDI"), List.of("The strongest model.")));
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/dispatch-rules.json"), carried);

        assertThat(rules.withCarriedReasons(example, Map.of(), Map.of())).isEqualTo(example);
        assertThat(rules.validatedAgainst(MachineProfile.workerRosterIn(ExampleHome.file("config/machine.yaml"))).categories())
                .filteredOn(category -> category.id().equals(CategoryId.RESEARCH)).extracting(DispatchCategory::why).singleElement().asString()
                .endsWith("diagnosis costs a whole implementation round. The old harness's routing rules for this work said: Plans decide rounds."
                        + " Findings aim later work.");
        assertThat(carried).contains("nothing more, nothing less. The old harness's routing rules for this work said: The strongest model.");
    }
}
