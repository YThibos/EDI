package io.github.ythibos.edi.web.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.WaitingBoard;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardBinding;
import io.github.ythibos.edi.domain.board.BoardId;
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
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.PresenterFixtures;

class CaptainReportPresenterTest {

    private static final WaitingOnTheCaptain NOTHING_WAITING = new WaitingOnTheCaptain(List.of(), List.of(), List.of());
    private static final Instant FIRST_DAY = Instant.parse("2026-10-13T08:00:00Z");

    private final CaptainReportPresenter presenter = PresenterFixtures.captainReportPresenter();

    @Test
    void replacesEveryGlyphThatCouldWidenALineAndJoinsBrokenLines() {
        Decision decision = Decision.raised(new DecisionNumber(58), new TaskId("lamp-choice"), new DecisionCard(
                "Which lamp 💡?",
                "Both work;\nthe oil one\tsmokes.",
                List.of(new DecisionOption(new OptionKey('A'), "LED ⭐")),
                new Recommendation(new OptionKey('A'), "it lasts 永")), PresenterFixtures.AT);
        Turn turn = new Turn(List.of(new TurnItem(List.of("harness"), "Shipped 🚀 today – Café €5")), List.of(), Map.of());

        String report = rendered(new FleetSnapshot(waitingFor(decision), List.of(), List.of(), List.of(), List.of(), List.of()), turn);

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

        String report = rendered(new FleetSnapshot(NOTHING_WAITING, List.of(), List.of(), queueOrder, List.of(), List.of()), Turn.NONE);

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

        String report = rendered(new FleetSnapshot(waitingFor(decision), List.of(), List.of(), List.of(), List.of(), List.of()), turn);

        assertThat(report).contains("╟── garden ───", "║  D58   Which lamp?\n").doesNotContain("Ignored for a decision", "╟── Fleet");
    }

    @Test
    void countsAWaitingBoardAndARequestReadyForReviewInTheDecisionZoneWithTheirLinks() {
        Task research = filed("lamp-research", 0);
        Task lamp = filed("fit-lamp", 0);
        Board board = new Board(new BoardId(3), research.id(), "/tmp/board.html", BoardRounds.LINK, List.of(), 0, 1, FIRST_DAY, null, null);
        MergeRequestAddress request = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 14);
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(), List.of(new WaitingBoard(board, research, List.of())),
                List.of(new ReadyForReview(lamp, new Delivery(lamp.id(), request, true, "abc123", MergeRequestState.OPEN, null, FIRST_DAY, FIRST_DAY))));

        String report = rendered(new FleetSnapshot(waiting, List.of(), List.of(), List.of(), List.of(), List.of()), Turn.NONE);

        assertThat(report).contains(
                "╔══ NEEDS YOUR DECISION ══ 2 open ══",
                "╟── harness ───",
                "║  Board for Stow the lamp-research: 1 question waiting at " + BoardRounds.LINK + "\n",
                "║  Ready for your review: Stow the fit-lamp - https://github.com/example-owner/harness/pull/14\n")
                .doesNotContain("Nothing waiting on you");
    }

    @Test
    void drawsADecisionBoundToAWaitingBoardAsOneLineUnderItWrappedPastItsNumber() {
        Task research = filed("lamp-research", 0);
        Decision decision = Decision.raised(new DecisionNumber(58), research.id(), new DecisionCard(
                "How the harness learns whether a lamp exists in the store and must be fitted again",
                "A long context the board shows in full.",
                List.of(new DecisionOption(new OptionKey('A'), "Fit first")),
                new Recommendation(new OptionKey('A'), "it is proven")), PresenterFixtures.AT);
        Board board = new Board(new BoardId(3), research.id(), "/tmp/board.html", "http://127.0.0.1:4387/s/1", List.of(BoardBinding.byNumber(
                decision.number())), 0, 4, FIRST_DAY, null, null);
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(), List.of(new WaitingBoard(board, research, List.of(new OpenDecision(decision,
                research)))), List.of());

        String report = presenter.text(new CaptainReport(new FleetSnapshot(waiting, List.of(), List.of(), List.of(), List.of(), List.of()), Turn.NONE,
                ReportWidth.forPane(64), Presence.presentSince(TestHome.START), null));

        assertThat(report).startsWith("""
                ╔══ NEEDS YOUR DECISION ══ 2 open ════════════════════════════
                ║
                ╟── harness ─────────────────────────────────
                ║
                ║  Board for Stow the lamp-research: 4 questions waiting at
                ║        http://127.0.0.1:4387/s/1
                ║        D58   How the harness learns whether a lamp exists in
                ║              the store and must be fitted again
                ║
                ╚═════════════════════════════════════════════════════════════
                """).doesNotContain("A long context", "Fit first", "Recommended");
    }

    private static WaitingOnTheCaptain waitingFor(Decision decision) {
        return new WaitingOnTheCaptain(List.of(new OpenDecision(decision, PresenterFixtures.captainItem())), List.of(), List.of());
    }

    private String rendered(FleetSnapshot fleet, Turn turn) {
        return presenter.text(new CaptainReport(fleet, turn, ReportWidth.standard(), Presence.presentSince(TestHome.START),
                null));
    }

    private static Task filed(String item, int daysLater) {
        return Task.queued(new TaskId(item), new Intake(TaskKind.SHIP, "Stow the " + item, List.of(new ProjectName("harness")), null, null, List.of(), null),
                "", FIRST_DAY.plus(daysLater, ChronoUnit.DAYS));
    }
}
