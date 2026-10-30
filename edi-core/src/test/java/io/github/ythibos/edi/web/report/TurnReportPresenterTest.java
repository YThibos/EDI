package io.github.ythibos.edi.web.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.web.PresenterFixtures;

class TurnReportPresenterTest {

    private static final Instant NOW = PresenterFixtures.AT;
    private static final Instant PREVIOUS_REPORT = NOW.minus(Duration.ofHours(1));
    private static final WaitingOnTheCaptain NOTHING_WAITING = new WaitingOnTheCaptain(List.of(), List.of(), List.of(), List.of());
    private static final ReportWidth WIDTH = new ReportWidth(72);

    private final TurnReportPresenter presenter = PresenterFixtures.turnReportPresenter();

    @Test
    void saysInOneLineThatNothingNeedsTheCaptain() {
        String report = presenter.text(new TurnReport(NOTHING_WAITING, Turn.NONE, WIDTH, null, PREVIOUS_REPORT));

        assertThat(report).isEqualTo("Nothing needs you; /sitrep shows the rest.\n");
    }

    @Test
    void drawsLongOpenDecisionsOnOneLineAndANewDecisionInFullBesideTheReviewAndTheTurn() {
        Task lamp = captainItem("lamp-choice", "harness");
        Task bell = captainItem("bell-choice", "harness");
        Task paint = captainItem("paint-choice", "garden");
        Task fitting = captainItem("fit-lamp", "harness");
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(
                new OpenDecision(raised(41, lamp, "Which lamp?", NOW.minus(Duration.ofDays(9))), lamp),
                new OpenDecision(raised(44, bell, "Which bell rings the hour, the brass one or the iron one by the gate?", NOW.minus(Duration.ofDays(2))), bell),
                new OpenDecision(raised(58, paint, "Which paint?", NOW), paint)),
                List.of(), List.of(readyForReview(fitting)), List.of());
        Turn turn = new Turn(List.of(new TurnItem(List.of("harness"), "Fitted the lamp bracket")), List.of(new TurnItem(List.of(), "Choose the paint")),
                Map.of());

        String report = presenter.text(new TurnReport(waiting, turn, WIDTH, null, PREVIOUS_REPORT));

        assertThat(report).isEqualTo("""
                ╔══ NEEDS YOUR DECISION ══ 4 open ══════════════════════════════════════
                ║
                ╟── harness ─────────────────────────────────
                ║
                ║  D41   Which lamp? » Recommended: A - The simple way
                ║
                ║  D44   Which bell rings the hour, the brass one or the iron one by the
                ║        gate? » Recommended: A - The simple way
                ║
                ║  Ready for your review: Choose fit-lamp -
                ║        https://github.com/example-owner/harness/pull/14
                ║
                ╟── garden ──────────────────────────────────
                ║
                ║  D58   Which paint?
                ║
                ║        (A)  The simple way
                ║
                ║        (B)  The thorough way
                ║
                ║        » Recommended: A - it is enough
                ║
                ╚═══════════════════════════════════════════════════════════════════════

                DONE THIS TURN  (1)
                ───────────────────
                  harness           • Fitted the lamp bracket

                NEXT
                ────
                  Fleet             • Choose the paint
                """);
    }

    @Test
    void drawsEveryCardInFullWhenTheCaptainSawNoEarlierReport() {
        Task lamp = captainItem("lamp-choice", "harness");
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(new OpenDecision(raised(41, lamp, "Which lamp?", NOW.minus(Duration.ofDays(9))), lamp)),
                List.of(), List.of(), List.of());

        String report = presenter.text(new TurnReport(waiting, Turn.NONE, WIDTH, null, null));

        assertThat(report).contains("║  D41   Which lamp?\n", "║        (A)  The simple way\n", "║        » Recommended: A - it is enough\n");
    }

    @Test
    void drawsADecisionRaisedAtThePreviousReportOnOneLine() {
        Task lamp = captainItem("lamp-choice", "harness");
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(new OpenDecision(raised(41, lamp, "Which lamp?", PREVIOUS_REPORT), lamp)),
                List.of(), List.of(), List.of());

        String report = presenter.text(new TurnReport(waiting, Turn.NONE, WIDTH, null, PREVIOUS_REPORT));

        assertThat(report).contains("║  D41   Which lamp? » Recommended: A - The simple way\n").doesNotContain("(A)");
    }

    @Test
    void leavesTheDecisionZoneOutWhenNothingWaitsOnTheCaptain() {
        Turn turn = new Turn(List.of(new TurnItem(List.of("harness"), "Fitted the lamp bracket")), List.of(), Map.of());

        String report = presenter.text(new TurnReport(NOTHING_WAITING, turn, WIDTH, null, PREVIOUS_REPORT));

        assertThat(report).isEqualTo("""
                DONE THIS TURN  (1)
                ───────────────────
                  harness           • Fitted the lamp bracket
                """);
    }

    @Test
    void headsTheReportWithTheCalendarItCannotRead() {
        UnreadableCalendar unreadable = new UnreadableCalendar(Instant.parse("2026-10-15T08:02:00Z"), "Outlook did not answer.");

        String report = presenter.text(new TurnReport(NOTHING_WAITING, Turn.NONE, WIDTH, unreadable, PREVIOUS_REPORT));

        assertThat(report).isEqualTo("""
                Calendar: I can't read your calendar since 2026-10-15 08:02 UTC, so a
                  meeting added since won't switch me to quiet: "Outlook did not answer."
                """);
    }

    private static Task captainItem(String id, String project) {
        return Task.queued(new TaskId(id), new Intake(TaskKind.CAPTAIN, "Choose " + id.replace("-choice", ""), List.of(new ProjectName(project)), null, null,
                List.of(), null), "", NOW);
    }

    private static Decision raised(int number, Task task, String title, Instant at) {
        return Decision.raised(new DecisionNumber(number), task.id(), new DecisionCard(title, "",
                List.of(new DecisionOption(new OptionKey('A'), "The simple way"), new DecisionOption(new OptionKey('B'), "The thorough way")),
                new Recommendation(new OptionKey('A'), "it is enough")), at);
    }

    private static ReadyForReview readyForReview(Task task) {
        MergeRequestAddress request = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 14);
        return new ReadyForReview(task, new Delivery(task.id(), request, true, "abc123", MergeRequestState.OPEN, null, NOW, NOW));
    }
}
