package io.github.ythibos.edi.application.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.adapter.fork.ForkRoutingRule;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;

class RoutingRulesImportTest {

    @Test
    void carriesEachOldReasonIntoTheCategoryOrOverrideItsRuleBecame() {
        MigrationNotes notes = new MigrationNotes();

        RoutingRulesImport carried = RoutingRulesImport.of(List.of(
                new ForkRoutingRule("The task is research or planning: a scout.", "Plans decide rounds."),
                new ForkRoutingRule("The task is an investigation whose cause is genuinely unknown.", "Findings aim later work."),
                new ForkRoutingRule("The task is a trivial mechanical edit with one outcome.", "The cheapest model."),
                new ForkRoutingRule("The task is research, design or implementation for E.D.I. itself.", "The strongest model."),
                new ForkRoutingRule("The task is a captain-feedback micro-fix.", " ")), notes);

        assertThat(carried.reasonsByCategory()).containsExactlyInAnyOrderEntriesOf(Map.of(
                CategoryId.RESEARCH, List.of("Plans decide rounds.", "Findings aim later work."),
                CategoryId.MECHANICAL, List.of("The cheapest model.")));
        assertThat(carried.reasonsByProject()).containsExactlyEntriesOf(Map.of(new ProjectName("EDI"), List.of("The strongest model.")));
        assertThat(notes.all()).isEmpty();
    }

    @Test
    void listsTheOldRulesItCarriesNothingOf() {
        MigrationNotes notes = new MigrationNotes();

        RoutingRulesImport carried = RoutingRulesImport.of(List.of(
                new ForkRoutingRule("The task is a routine upstream sync of this fork.", "Syncs are small."),
                new ForkRoutingRule("The task paints the boats in the harbour before the long winter storms arrive.", "Paint dries.")), notes);

        assertThat(carried.reasonsByCategory()).isEmpty();
        assertThat(notes.all()).extracting(MigrationNote::subject, MigrationNote::note).containsExactly(
                tuple(RoutingRulesImport.DISPATCH_RULES,
                        "carries nothing of the old rule for \"The task is a routine upstream sync of...\": it routed work on the old harness itself"),
                tuple(RoutingRulesImport.DISPATCH_RULES, "carries nothing of the old rule for \"The task paints the boats in the harbour...\":"
                        + " it fits no dispatch category, and its reason stays in the archived crew-dispatch.json"));
    }
}
