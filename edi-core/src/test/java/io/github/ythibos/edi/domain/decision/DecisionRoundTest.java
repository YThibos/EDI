package io.github.ythibos.edi.domain.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Holds;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.StageTrigger;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

class DecisionRoundTest {

    private static final Instant OPENED = Instant.parse("2026-11-09T09:00:00Z");
    private static final Task SHIP = task(TaskKind.SHIP);

    @Test
    void aDecisionOfARunningShipWaitsForItsRoundUnlessItIsUrgent() {
        assertThat(DecisionRound.waitsForItsRound(raised(58, OPENED, false), working(SHIP))).isTrue();
        assertThat(DecisionRound.waitsForItsRound(raised(58, OPENED, true), working(SHIP))).isFalse();
    }

    @Test
    void aDecisionOfAQueuedShipOrOfACaptainItemReachesTheCaptainAtOnce() {
        assertThat(DecisionRound.waitsForItsRound(raised(58, OPENED, false), SHIP)).isFalse();
        assertThat(DecisionRound.waitsForItsRound(raised(58, OPENED, false), task(TaskKind.CAPTAIN))).isFalse();
    }

    @Test
    void opensAtTheFirstDecisionRaisedAndGathersTheWaitingDecisionsInTheOrderRaised() {
        Decision later = raised(59, OPENED.plus(Duration.ofMinutes(3)), false);
        Decision first = raised(58, OPENED, false);

        assertThat(DecisionRound.gatheredFrom(List.of(later, first)))
                .contains(new DecisionRound(OPENED, List.of(new DecisionNumber(58), new DecisionNumber(59))));
        assertThat(DecisionRound.gatheredFrom(List.of())).isEmpty();
    }

    @Test
    void isDueOnceItsWindowHasPassed() {
        DecisionRound round = new DecisionRound(OPENED, List.of(new DecisionNumber(58)));

        assertThat(round.closes()).isEqualTo(Instant.parse("2026-11-09T09:05:00Z"));
        assertThat(round.isDue(round.closes().minusSeconds(1))).isFalse();
        assertThat(round.isDue(round.closes())).isTrue();
    }

    private static Task working(Task queued) {
        return queued.movedBy(queued.transitions().move(queued.stage(), new StageTrigger.Dispatch(Holds.none(), List.of(), true)), OPENED);
    }

    private static Task task(TaskKind kind) {
        return Task.queued(new TaskId("fit-lamp"), new Intake(kind, "Fit the lamp", List.of(new ProjectName("harness")), null, null, List.of(), null), "",
                OPENED);
    }

    private static Decision raised(int number, Instant at, boolean urgent) {
        DecisionCard card = new DecisionCard("Which lamp?", "", List.of(new DecisionOption(new OptionKey('A'), "LED")),
                new Recommendation(new OptionKey('A'), "it lasts"), urgent);
        return Decision.raised(new DecisionNumber(number), SHIP.id(), card, at);
    }
}
