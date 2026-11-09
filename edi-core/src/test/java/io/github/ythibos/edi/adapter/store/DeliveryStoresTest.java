package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.delivery.CloneRefresh;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class DeliveryStoresTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final MergeRequestAddress DRAFT =
            new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITLAB, "forge.example.org", "team/harness"), 5);

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void storeATask() throws IOException {
        home = TestHome.in(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
    }

    @Test
    void keepsADeliveryAndFindsItByTaskUrlAndState() {
        Delivery open = Delivery.of(LAMP, new MergeRequestView(DRAFT, MergeRequestState.OPEN, true, "feature/HRN-3", "main", "2222", null, null),
                TestHome.START);
        home.deliveries().save(open);
        Delivery merged = open.observed(new MergeRequestView(DRAFT, MergeRequestState.MERGED, true, "feature/HRN-3", "main", "2222", "3333", TestHome.START.plusSeconds(600)),
                TestHome.START.plusSeconds(600));
        home.deliveries().save(merged);

        assertThat(home.deliveries().of(LAMP)).contains(merged);
        assertThat(home.deliveries().at(DRAFT.url())).contains(merged);
        assertThat(home.deliveries().inState(MergeRequestState.MERGED)).containsExactly(merged);
        assertThat(home.deliveries().inState(MergeRequestState.OPEN)).isEmpty();
    }

    @Test
    void refusesADeliveryWhosePartsDoNotRebuildItsUrl() {
        home.deliveries().save(Delivery.of(LAMP, new MergeRequestView(DRAFT, MergeRequestState.OPEN, true, "feature/HRN-3", "main", "2222", null, null),
                TestHome.START));
        home.jdbc().sql("update delivery set url = 'https://forge.example.org/elsewhere/-/merge_requests/5'").update();

        assertThatIllegalStateException().isThrownBy(() -> home.deliveries().of(LAMP)).withMessageContaining("the record is refused");
    }

    @Test
    void keepsTheLatestReadingOfAPipelineRun() {
        home.pipelineRuns().save(LAMP, new PipelineRun("01A", "running", "review", "2222", null), TestHome.START);
        home.pipelineRuns().save(LAMP, new PipelineRun("01A", "running", "ci", "2222", DRAFT.url()), TestHome.START.plusSeconds(60));

        assertThat(home.pipelineRuns().latestOf(LAMP)).contains(new PipelineRun("01A", "running", "ci", "2222", DRAFT.url()));
        assertThat(home.pipelineRuns().latestOf(new TaskId("tune-horn"))).isEmpty();
    }

    @Test
    void keepsARefreshPendingUntilVerifiedAndItsCleanUpUntilTried() {
        CloneRefresh refresh = CloneRefresh.awaiting(LAMP, new ProjectName("harness"), new BaseBranch("main"), "3333", TestHome.START);
        home.cloneRefreshStore().save(refresh);

        assertThat(home.cloneRefreshStore().pendingFor(new ProjectName("harness"))).containsExactly(refresh);
        assertThat(home.cloneRefreshStore().verifiedAwaitingCleanUp()).isEmpty();

        home.cloneRefreshStore().save(refresh.verified(TestHome.START));
        assertThat(home.cloneRefreshStore().pending()).isEmpty();
        assertThat(home.cloneRefreshStore().verifiedAwaitingCleanUp()).containsExactly(refresh.verified(TestHome.START));

        home.cloneRefreshStore().save(refresh.verified(TestHome.START).cleanUpTried(TestHome.START));
        assertThat(home.cloneRefreshStore().verifiedAwaitingCleanUp()).isEmpty();
    }

    @Test
    void keepsATasksCostWithAnUnknownPipelineSpend() {
        TaskCost unknown = new TaskCost(LAMP, new TokenUsage(1, 2, 3, 4), null, TestHome.START);
        home.taskCosts().save(unknown);

        assertThat(home.taskCosts().of(LAMP)).contains(unknown);
        assertThat(home.taskCosts().of(LAMP).orElseThrow().pipelineIfKnown()).isEmpty();
    }

    @Test
    void keepsEachCountOfThePipelinesSpendInItsOwnColumn() {
        TaskCost cost = new TaskCost(LAMP, new TokenUsage(1, 2, 3, 4), new TokenUsage(40, 900000, 5000, 700), TestHome.START);
        home.taskCosts().save(cost);

        assertThat(home.jdbc().sql("""
                        select pipeline_input_tokens, pipeline_cache_read_tokens, pipeline_cache_write_tokens, pipeline_output_tokens
                        from task_cost where task_id = :task""")
                .param("task", LAMP.value())
                .query((row, _) -> List.of(row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4)))
                .single()).containsExactly(40L, 900000L, 5000L, 700L);
        assertThat(home.taskCosts().of(LAMP)).contains(cost);
    }
}
