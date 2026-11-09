package io.github.ythibos.edi.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.web.console.SitrepConsolePresenter;
import io.github.ythibos.edi.web.decision.DecisionLines;
import io.github.ythibos.edi.web.personality.PersonalityText;
import io.github.ythibos.edi.web.presence.PresenceWords;
import io.github.ythibos.edi.web.presence.ReturnBriefText;
import io.github.ythibos.edi.web.primary.SessionStartDigestText;
import io.github.ythibos.edi.web.report.CaptainReportPresenter;
import io.github.ythibos.edi.web.report.TurnReportPresenter;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.worker.HealthWords;
import io.github.ythibos.edi.web.worker.WorkerListText;

public final class PresenterFixtures {

    public static final Instant AT = Instant.parse("2026-10-15T09:30:00Z");

    private PresenterFixtures() {
    }

    public static Moments moments() {
        return new Moments(new ManualClock(AT));
    }

    public static PresenceWords presenceWords() {
        return new PresenceWords(moments());
    }

    public static HealthWords healthWords() {
        return new HealthWords(moments());
    }

    public static DecisionLines decisionLines() {
        return new DecisionLines(moments());
    }

    public static WorkerListText workerListText() {
        return new WorkerListText(healthWords());
    }

    public static SessionStartDigestText sessionStartDigestText() {
        return new SessionStartDigestText(workerListText(), new PersonalityText(), presenceWords(), healthWords(), moments());
    }

    public static CaptainReportPresenter captainReportPresenter() {
        return new CaptainReportPresenter(presenceWords());
    }

    public static TurnReportPresenter turnReportPresenter() {
        return new TurnReportPresenter(presenceWords(), moments());
    }

    public static ReturnBriefText returnBriefText() {
        return new ReturnBriefText(presenceWords());
    }

    public static SitrepConsolePresenter sitrepConsolePresenter() {
        return new SitrepConsolePresenter(presenceWords());
    }

    public static Task lantern() {
        return Task.queued(new TaskId("lantern"),
                new Intake(TaskKind.SHIP, "Rework the lantern", List.of(new ProjectName("harness"), new ProjectName("garden")), WorkKey.parse("HRN-3"), null, List.of(), null),
                "", AT);
    }

    public static Task heldHorn() {
        return Task.queued(new TaskId("fog-horn"),
                        new Intake(TaskKind.SHIP, "Tune the horn", List.of(new ProjectName("harness")), null, null, List.of(new TaskId("lantern")), null),
                        "", AT)
                .held(new CaptainHold(HoldKind.DEFERRAL, "after the storm", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT));
    }

    public static Task captainItem() {
        return Task.queued(new TaskId("lamp-choice"), new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of(), null), "", AT);
    }

    public static Decision openDecision() {
        return Decision.raised(new DecisionNumber(58), new TaskId("lamp-choice"), Cards.card("Which lamp?"), AT);
    }

    public static Decision answeredDecision() {
        return openDecision().answered(new CaptainAnswer("D58, A\nand keep the old one", new OptionKey('A'), "The simple lamp.", AT.plusSeconds(600), AnswerChannel.CHAT));
    }
}
