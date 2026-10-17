package io.github.ythibos.edi.web.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.PresenterFixtures;

class CaptainReportPresenterTest {

    private static final Instant FIRST_DAY = Instant.parse("2026-10-13T08:00:00Z");

    private final CaptainReportPresenter presenter = new CaptainReportPresenter();

    @Test
    void replacesEveryGlyphThatCouldWidenALineAndJoinsBrokenLines() {
        Decision decision = Decision.raised(new DecisionNumber(58), new TaskId("lamp-choice"), new DecisionCard(
                "Which lamp 💡?",
                "Both work;\nthe oil one\tsmokes.",
                List.of(new DecisionOption(new OptionKey('A'), "LED ⭐")),
                new Recommendation(new OptionKey('A'), "it lasts 永")), PresenterFixtures.AT);
        Turn turn = new Turn(List.of(new TurnItem(List.of("harness"), "Shipped 🚀 today – Café €5")), List.of(), Map.of());

        String report = rendered(new FleetSnapshot(List.of(new OpenDecision(decision, PresenterFixtures.captainItem())), List.of(), List.of(), List.of(), List.of(), List.of()), turn);

        assertThat(report).contains(
                "║  D58   Which lamp ??\n",
                "║        Both work; the oil one smokes.\n",
                "║        (A)  LED ?\n",
                "║        » Recommended: A - it lasts ?\n",
                "  harness           • Shipped ? today – Café €5\n");
    }

    @Test
    void listsTheFiveNewestFiledTasksOfAQuietBacklogAndCountsTheRest() {
        List<Task> queueOrder = List.of(
                filed("anchor", 0), filed("bell", 2), filed("chart", 1), filed("davit", 2), filed("ensign", 0), filed("fender", 1), filed("gangway", 0));

        String report = rendered(new FleetSnapshot(List.of(), List.of(), List.of(), queueOrder, List.of(), List.of()), Turn.NONE);

        assertThat(report).endsWith("""

                BACKLOG  (7)
                ────────────
                  harness           • Stow the bell
                                    • Stow the davit
                                    • Stow the chart
                                    • Stow the fender
                                    • Stow the anchor
                  (+2 more queued)
                """);
    }

    @Test
    void groupsADecisionUnderTheProjectsTheTurnGivesItsTask() {
        Decision decision = PresenterFixtures.openDecision();
        Turn turn = new Turn(List.of(), List.of(), Map.of("lamp-choice", new TaskWording("Ignored for a decision", null, List.of("garden"))));

        String report = rendered(new FleetSnapshot(List.of(new OpenDecision(decision, PresenterFixtures.captainItem())), List.of(), List.of(), List.of(), List.of(), List.of()), turn);

        assertThat(report).contains("╟── garden ───", "║  D58   Which lamp?\n").doesNotContain("Ignored for a decision", "╟── Fleet");
    }

    private String rendered(FleetSnapshot fleet, Turn turn) {
        return presenter.text(new CaptainReport(fleet, turn, ReportWidth.standard(), Presence.presentSince(TestHome.START)));
    }

    private static Task filed(String item, int daysLater) {
        return Task.queued(new TaskId(item), new Intake(TaskKind.SHIP, "Stow the " + item, List.of(new ProjectName("harness")), null, null, List.of(), null),
                "", FIRST_DAY.plus(daysLater, ChronoUnit.DAYS));
    }
}
