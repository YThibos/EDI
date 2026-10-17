package io.github.ythibos.edi.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.UnplannedShip;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkEvents;
import io.github.ythibos.edi.testing.WorkerFixtures;

class ResearchFirstPilotTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private int lastKey = 100;

    @BeforeEach
    void finishTwoShipsWithoutResearch() throws IOException {
        home = TestHome.in(directory);
        for (int number = 1; number <= 2; number++) {
            finishedShip("unplanned-" + number, null, 2_000_000, new PipelineReview(3, 4));
        }
    }

    @Test
    void putsTheComparisonBeforeTheCaptainOnceTenPlannedShipsHaveFinished() {
        for (int number = 1; number <= 9; number++) {
            finishedShip("planned-" + number, "plan-" + number, 1_000_000, new PipelineReview(1, 1));
        }
        home.eventFollowers().followTheLog();
        assertThat(home.tasks().contains(ResearchFirstPilot.DECISION_TASK)).isFalse();

        finishedShip("planned-10", "plan-10", 1_000_000, new PipelineReview(1, 1));
        home.eventFollowers().followTheLog();

        assertThat(home.tasks().get(ResearchFirstPilot.DECISION_TASK).intake().kind()).isEqualTo(TaskKind.CAPTAIN);
        Decision decision = home.decisions().ofTask(ResearchFirstPilot.DECISION_TASK).orElseThrow();
        assertThat(decision.card().context()).contains(
                "Research first planned 10 ships; 2 comparable unplanned ships ran without it.",
                "total tokens, research included: 1,500,000 against 2,000,000;",
                "review rounds: 1.0 against 3.0;");
        assertThat(decision.card().recommendation().key().letter()).isEqualTo('A');
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.DECISION_RAISED);
    }

    @Test
    void raisesTheComparisonOnlyOnce() {
        for (int number = 1; number <= 11; number++) {
            finishedShip("planned-" + number, "plan-" + number, 1_000_000, new PipelineReview(1, 1));
            home.eventFollowers().followTheLog();
        }

        assertThat(home.eventTypesOf(ResearchFirstPilot.DECISION_TASK)).containsExactly("TaskAdded", "DecisionRaised");
    }

    private void finishedShip(String ship, String research, long tokens, PipelineReview review) {
        TaskId shipId = new TaskId(ship);
        home.storing(List.of(WorkerFixtures.queuedShip(ship, "harness", "HRN-" + ++lastKey)));
        TaskId researchId = research == null ? null : finishedResearch(research, tokens);
        home.unplannedShips().insert(new UnplannedShip(shipId, CategoryId.IMPLEMENTATION_UNPLANNED, researchId, TestHome.START));
        home.taskCosts().save(new TaskCost(shipId, new TokenUsage(tokens, 0, 0, 0), TokenUsage.NONE, TestHome.START));
        home.reviews().save(shipId, review, TestHome.START);
        home.markDone(shipId);
        WorkEvents.of(home, ship).cleanedUp();
    }

    private TaskId finishedResearch(String research, long tokens) {
        TaskId researchId = new TaskId(research);
        home.storing(List.of(WorkerFixtures.queued(research, TaskKind.SCOUT, "harness", null)));
        home.taskCosts().save(new TaskCost(researchId, new TokenUsage(tokens / 2, 0, 0, 0), TokenUsage.NONE, TestHome.START));
        home.markDone(researchId);
        return researchId;
    }
}
