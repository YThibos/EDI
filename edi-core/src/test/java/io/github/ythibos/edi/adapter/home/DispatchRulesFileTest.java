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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class DispatchRulesFileTest {

    @TempDir
    private Path home;

    @Test
    void carriesOldReasonsIntoTheCategoriesAndOverridesTheyBelongTo() throws IOException {
        String example = ExampleHome.file("config/dispatch-rules.json");
        DispatchRulesFile rules = new DispatchRulesFile(new EdiHome(home), new JsonMapper());

        String carried = rules.withWhatItLacks(example, example,
                Map.of(CategoryId.RESEARCH, List.of("Plans decide rounds.", "Findings aim later work.")),
                Map.of(new ProjectName("EDI"), List.of("The strongest model.")));
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/dispatch-rules.json"), carried);

        assertThat(rules.withWhatItLacks(example, example, Map.of(), Map.of())).isEqualTo(example);
        assertThat(rules.validatedAgainst(MachineProfile.workerRosterIn(ExampleHome.file("config/machine.yaml"))).categories())
                .filteredOn(category -> category.id().equals(CategoryId.RESEARCH)).extracting(DispatchCategory::why).singleElement().asString()
                .endsWith("diagnosis costs a whole implementation round. The old harness's routing rules for this work said: Plans decide rounds."
                        + " Findings aim later work.");
        assertThat(carried).contains("nothing more, nothing less. The old harness's routing rules for this work said: The strongest model.");
    }

    @Test
    void keepsEveryValueTheHomeHoldsAndAddsOnlyWhatItLacks() throws IOException {
        String example = ExampleHome.file("config/dispatch-rules.json");
        DispatchRulesFile rules = new DispatchRulesFile(new EdiHome(home), new JsonMapper());
        String tuned = """
                {
                  "schema": "edi-dispatch.v2",
                  "categories": [
                    { "id": "research", "when": "Tuned.", "use": { "model": "claude-sonnet-5-5", "effort": "low" }, "why": "Tuned on the shadow run." }
                  ]
                }
                """;
        Map<CategoryId, List<String>> reasons = Map.of(CategoryId.RESEARCH, List.of("Plans decide rounds."));

        String merged = rules.withWhatItLacks(tuned, example, reasons, Map.of(new ProjectName("EDI"), List.of("The strongest model.")));
        JsonNode research = categoryOf(merged, "research");

        assertThat(research.path("use").path("effort").asString()).isEqualTo("low");
        assertThat(research.path("why").asString())
                .isEqualTo("Tuned on the shadow run. The old harness's routing rules for this work said: Plans decide rounds.");
        assertThat(new JsonMapper().readTree(merged).path("categories").valueStream().map(category -> category.path("id").asString()).toList())
                .containsAll(new JsonMapper().readTree(example).path("categories").valueStream().map(category -> category.path("id").asString())
                        .toList());
        assertThat(rules.withWhatItLacks(merged, example, reasons, Map.of(new ProjectName("EDI"), List.of("The strongest model."))))
                .isEqualTo(merged);
    }

    private static JsonNode categoryOf(String rules, String id) {
        return new JsonMapper().readTree(rules).path("categories").valueStream().filter(category -> category.path("id").asString().equals(id))
                .findFirst().orElseThrow();
    }
}
