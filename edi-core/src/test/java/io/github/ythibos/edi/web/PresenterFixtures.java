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

public final class PresenterFixtures {

    public static final Instant AT = Instant.parse("2026-10-15T09:30:00Z");

    private PresenterFixtures() {
    }

    public static Task lantern() {
        return Task.queued(new TaskId("lantern"),
                new Intake(TaskKind.SHIP, "Rework the lantern", List.of(new ProjectName("harness"), new ProjectName("garden")), WorkKey.parse("HRN-3"), null, List.of()),
                "", AT);
    }

    public static Task heldHorn() {
        return Task.queued(new TaskId("fog-horn"),
                        new Intake(TaskKind.SHIP, "Tune the horn", List.of(new ProjectName("harness")), null, null, List.of(new TaskId("lantern"))),
                        "", AT)
                .held(new CaptainHold(HoldKind.DEFERRAL, "after the storm", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT));
    }

    public static Task captainItem() {
        return Task.queued(new TaskId("lamp-choice"), new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of()), "", AT);
    }

    public static Decision openDecision() {
        return Decision.raised(new DecisionNumber(58), new TaskId("lamp-choice"), Cards.card("Which lamp?"), AT);
    }

    public static Decision answeredDecision() {
        return openDecision().answered(new CaptainAnswer("D58, A\nand keep the old one", new OptionKey('A'), "The simple lamp.", AT.plusSeconds(600), AnswerChannel.CHAT));
    }
}
