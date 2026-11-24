package io.github.ythibos.edi.web.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.application.fleet.WorkerQuestion;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.HeadChecks;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.report.Turn;
import io.github.ythibos.edi.domain.report.TurnItem;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.BootId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.web.PresenterFixtures;

class TurnReportNamingTest {

    private static final Instant NOW = PresenterFixtures.AT;
    private static final ReportWidth WIDTH = new ReportWidth(72);
    private static final String ADDRESS = "https://github.com/example-owner/harness/pull/14";

    private final Task lamp = task("lamp-choice");
    private final Task fitting = task("fit-lamp");

    @Test
    void isNamedByAReplyThatNamesEveryOpenDecisionAndEveryReadyReviewByItsAddress() {
        TurnReport report = report(Turn.NONE);

        assertThat(report.isNamedIn(Reply.of("D41 is yours, and " + ADDRESS + " is ready for review."))).isTrue();
    }

    @Test
    void isNotNamedWhenTheReplyLeavesOutADecisionOrAReviewAddress() {
        TurnReport report = report(Turn.NONE);

        assertThat(report.isNamedIn(Reply.of("The review at " + ADDRESS + " is ready."))).isFalse();
        assertThat(report.isNamedIn(Reply.of("D41 is yours; a review is ready."))).isFalse();
        assertThat(report.isNamedIn(Reply.of("D410 is yours, and " + ADDRESS + " is ready."))).isFalse();
        assertThat(report.isNamedIn(Reply.of("D41 is yours, and " + ADDRESS + "2 is ready."))).isFalse();
        assertThat(report.isNamedIn(Reply.of(""))).isFalse();
        assertThat(report.isNamedIn(Reply.of(null))).isFalse();
    }

    @Test
    void isNamedDespiteAWorkerQuestionOnlyWhenADecisionTheReplyNamesWasRaisedOnItsTaskSinceItWasAsked() {
        Reply namingEveryItem = Reply.of("D41 is yours, and " + ADDRESS + " is ready for review.");

        assertThat(report(Turn.NONE, List.of(questionOn(lamp, NOW))).isNamedIn(namingEveryItem)).isTrue();
        assertThat(report(Turn.NONE, List.of(questionOn(fitting, NOW))).isNamedIn(namingEveryItem)).isFalse();
        assertThat(report(Turn.NONE, List.of(questionOn(lamp, NOW.plusSeconds(60)))).isNamedIn(namingEveryItem)).isFalse();
    }

    @Test
    void isNamedOnlyWhenTheReplySaysEveryRecordedItemInItsOwnWords() {
        Turn turn = new Turn(List.of(new TurnItem(List.of("harness"), "Fitted the lamp bracket")), List.of(new TurnItem(List.of(), "Choose the paint")), Map.of());
        TurnReport report = new TurnReport(new WaitingOnTheCaptain(List.of(), List.of(), List.of(), List.of()), turn, WIDTH, null, null, null);

        assertThat(report.isNamedIn(Reply.of("I **fitted the  lamp bracket**.\nNext: choose the paint."))).isTrue();
        assertThat(report.isNamedIn(Reply.of("I fitted the lamp bracket."))).isFalse();
    }

    private TurnReport report(Turn turn) {
        return report(turn, List.of());
    }

    private TurnReport report(Turn turn, List<WorkerQuestion> questions) {
        MergeRequestAddress request = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 14);
        ReadyForReview ready = new ReadyForReview(fitting, new Delivery(fitting.id(), request, true, "abc123", HeadChecks.UNDECIDED, MergeRequestState.OPEN, null, NOW, NOW));
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(new OpenDecision(raised(41, lamp), lamp, false)), List.of(), List.of(ready), questions);
        return new TurnReport(waiting, turn, WIDTH, null, null, null);
    }

    private static WorkerQuestion questionOn(Task task, Instant askedAt) {
        Worker launched = Worker.launched(WorkerId.first(task.id()), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), new WorkerWindow("edi", 1L, NOW, "@1", 2L),
                new WorkerLaunch(LaunchKind.DISPATCH, NOW, new BootId("boot"), LoginName.DEFAULT));
        Worker asking = launched.reported(new StatusReport(StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Which lamp?"), askedAt);
        return new WorkerQuestion(task, asking, Duration.ofMinutes(5));
    }

    private static Task task(String id) {
        return Task.queued(new TaskId(id), new Intake(TaskKind.CAPTAIN, "Choose " + id, List.of(new ProjectName("harness")), null, null, List.of(), null, null), "",
                NOW);
    }

    private static Decision raised(int number, Task task) {
        return Decision.raised(new DecisionNumber(number), task.id(), new DecisionCard("Which lamp?", "",
                List.of(new DecisionOption(new OptionKey('A'), "The simple way"), new DecisionOption(new OptionKey('B'), "The thorough way")),
                new Recommendation(new OptionKey('A'), "it is enough")), NOW);
    }
}
