package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.RuleFirings.FiringCount;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleFired;
import io.github.ythibos.edi.domain.rule.RuleOutcome;
import io.github.ythibos.edi.domain.rule.RuleReason;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class RuleFiringsTest {

    private static final Instant AT = Instant.parse("2026-11-09T09:00:00Z");
    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final UUID SESSION = UUID.fromString("0d6c9e7a-6c63-4f0e-9a77-1a2b3c4d5e6f");

    @TempDir
    private Path directory;

    @Test
    void keepsOneRowForEachFiringWithItsRuleOutcomeCallerAndReason() throws IOException {
        TestHome home = TestHome.in(directory);

        new RuleFirings(home.jdbc()).record(RuleFired.refused(Rule.FORCED_PUSH, Actor.worker(WorkerId.parse("fit-lamp#1")), LAMP,
                "a forced push rewrites history on the remote (I-03)."), AT);
        new RuleFirings(home.jdbc()).record(new RuleFired(Rule.SECRET, RuleOutcome.ALLOWED_AFTER_A_CHECK, Actor.PRIMARY, null, SESSION,
                RuleReason.of("the captain's word lets the primary read it.")), AT.plusSeconds(1));

        assertThat(home.jdbc().sql("select at, rule, outcome, actor, task_id, session_id, reason from rule_fired order by number")
                .query((row, _) -> Arrays.asList(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5),
                        row.getString(6), row.getString(7)))
                .list())
                .containsExactly(
                        Arrays.asList("2026-11-09T09:00:00Z", "guard.forced-push", "refused", "worker:fit-lamp#1", "fit-lamp", null,
                                "a forced push rewrites history on the remote (I-03)."),
                        Arrays.asList("2026-11-09T09:00:01Z", "guard.secret", "allowed", "primary", null, SESSION.toString(),
                                "the captain's word lets the primary read it."));
    }

    @Test
    void countsTheFiringsOfEachRuleAndOutcomeSinceAMomentWithTheLastOne() throws IOException {
        TestHome home = TestHome.in(directory);
        RuleFirings firings = new RuleFirings(home.jdbc());
        firings.record(RuleFired.refused(Rule.MERGE, Actor.PRIMARY, null, "only the captain merges."), AT.minusSeconds(3600));
        firings.record(RuleFired.refused(Rule.MERGE, Actor.PRIMARY, null, "only the captain merges."), AT.plusMillis(250));
        firings.record(RuleFired.refused(Rule.MERGE, Actor.PRIMARY, null, "only the captain merges."), AT.plusSeconds(60));
        firings.record(RuleFired.warned(Rule.TICKET_UPDATE, Actor.CORE, null, "the tracker refused the link."), AT.plusSeconds(5));

        assertThat(firings.countedSince(AT)).extracting(FiringCount::rule, FiringCount::outcome, FiringCount::fired, FiringCount::lastFired)
                .containsExactly(
                        tuple("guard.merge", RuleOutcome.REFUSED, 2L, AT.plusSeconds(60)),
                        tuple("tracker.ticket-update", RuleOutcome.WARNED, 1L, AT.plusSeconds(5)));
    }
}
