package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.delivery.RecountedCosts.Recount;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class RecountTaskCostsTest {

    private static final LocalDate SINCE = LocalDate.parse("2026-11-02");
    private static final TokenUsage WORKERS = new TokenUsage(10, 200, 30, 4);
    private static final TokenUsage INFLATED = new TokenUsage(905040, 900000, 5000, 700);
    private static final TokenUsage SPENT = new TokenUsage(40, 900000, 5000, 700);

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void recountsThePipelinesSpendOfEveryCostCountedSinceTheDayAndKeepsWhatTheWorkersSpent() {
        TaskCost before = counted("fit-lamp", "HRN-3", INFLATED, "2026-11-02T08:00:00Z");
        home.pipeline().spent(SPENT);

        RecountedCosts recounted = home.recountTaskCosts().recountSince(SINCE, Actor.PRIMARY);

        TaskCost after = new TaskCost(before.task(), WORKERS, SPENT, before.countedAt());
        assertThat(recounted).isEqualTo(new RecountedCosts(SINCE, 1, List.of(new Recount(before, after))));
        assertThat(home.taskCosts().of(before.task())).contains(after);
        assertThat(home.eventTypesOf(before.task())).endsWith("TaskCostRecounted");
    }

    @Test
    void leavesACostCountedBeforeTheDayOrAlreadyRightOrWhoseSpendTheStoreNoLongerKnows() {
        TaskCost earlier = counted("fit-lamp", "HRN-3", INFLATED, "2026-11-01T23:59:59Z");
        TaskCost right = counted("paint-lamp", "HRN-4", SPENT, "2026-11-03T08:00:00Z");
        home.pipeline().spent(SPENT);

        assertThat(home.recountTaskCosts().recountSince(SINCE, Actor.PRIMARY).changed()).isEmpty();

        home.pipeline().spent(null);
        TaskCost unread = counted("hang-lamp", "HRN-5", INFLATED, "2026-11-04T08:00:00Z");

        assertThat(home.recountTaskCosts().recountSince(SINCE, Actor.PRIMARY)).isEqualTo(new RecountedCosts(SINCE, 2, List.of()));
        assertThat(home.taskCosts().of(earlier.task())).contains(earlier);
        assertThat(home.taskCosts().of(right.task())).contains(right);
        assertThat(home.taskCosts().of(unread.task())).contains(unread);
        assertThat(home.eventTypesOf(earlier.task())).doesNotContain("TaskCostRecounted");
    }

    private TaskCost counted(String task, String key, TokenUsage pipeline, String at) {
        DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip(task, "harness", key));
        TaskCost cost = new TaskCost(new TaskId(task), WORKERS, pipeline, Instant.parse(at));
        home.taskCosts().save(cost);
        return cost;
    }
}
