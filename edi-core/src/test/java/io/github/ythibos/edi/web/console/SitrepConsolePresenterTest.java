package io.github.ythibos.edi.web.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.LandedTask;
import io.github.ythibos.edi.application.fleet.OngoingTask;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ParkedTask;
import io.github.ythibos.edi.application.fleet.RecentAnswer;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.BootId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.web.PresenterFixtures;
import tools.jackson.databind.json.JsonMapper;

class SitrepConsolePresenterTest {

    private static final Instant NOW = PresenterFixtures.AT.plusSeconds(3_600);
    private static final MergeRequestAddress DRAFT = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 12);

    private final SitrepConsolePresenter presenter = new SitrepConsolePresenter();

    @Test
    void presentsTheDecisionDeckWithEachCardsOptionsAndRecommendation() {
        ConsoleSitrep sitrep = presented(new FleetSnapshot(List.of(new OpenDecision(PresenterFixtures.openDecision(), PresenterFixtures.captainItem())),
                List.of(), List.of(), List.of(), List.of(), List.of(new RecentAnswer(PresenterFixtures.answeredDecision(), PresenterFixtures.captainItem()))));

        assertThat(sitrep.schema()).isEqualTo("edi-sitrep.v1");
        assertThat(sitrep.address()).isEqualTo("Commander");
        assertThat(sitrep.machine()).isEqualTo("laptop");
        assertThat(sitrep.decisions()).singleElement().satisfies(card -> {
            assertThat(card.number()).isEqualTo("D58");
            assertThat(card.task()).isEqualTo("lamp-choice");
            assertThat(card.title()).isEqualTo("Which lamp?");
            assertThat(card.options()).extracting(ConsoleSitrep.Option::key, ConsoleSitrep.Option::text)
                    .containsExactly(tuple("A", "The simple way"), tuple("B", "The thorough way"));
            assertThat(card.recommended()).isEqualTo(new ConsoleSitrep.Recommendation("A", "it is enough"));
        });
        assertThat(sitrep.answered()).singleElement().satisfies(answer -> {
            assertThat(answer.verbatim()).isEqualTo("D58, A\nand keep the old one");
            assertThat(answer.option()).isEqualTo("A");
            assertThat(answer.channel()).isEqualTo("chat");
        });
    }

    @Test
    void tellsWhatEachOngoingTaskIsDoingFromItsWorkerAndItsPipeline() {
        Task horn = ship("horn", "Tune the horn");
        Task bell = ship("bell", "Polish the bell");
        Task mast = ship("mast", "Paint the mast");
        Worker reporting = workerOn(horn).reported(new StatusReport(StatusState.NEEDS_DECISION, null, null, "Brass or oil?"), NOW);
        PipelineRun reviewing = new PipelineRun("01RUN", "running", "review", "abc123", null);

        ConsoleSitrep sitrep = presented(new FleetSnapshot(List.of(), List.of(
                new OngoingTask(horn, reporting, null, delivery(horn)),
                new OngoingTask(bell, workerOn(bell), reviewing, null),
                new OngoingTask(mast, null, null, null)), List.of(), List.of(), List.of(), List.of()));

        assertThat(sitrep.ongoing()).extracting(ConsoleSitrep.Ongoing::task, ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::doing,
                ConsoleSitrep.Ongoing::draft).containsExactly(
                        tuple("horn", "needs-decision", "Brass or oil?", DRAFT.url()),
                        tuple("bell", "starting", "pipeline review", null),
                        tuple("mast", "waiting", "no worker yet", null));
    }

    @Test
    void chartsQueuedAndParkedWorkNewestFirstAndCountsWhatItLeavesOut() {
        List<Task> queued = IntStream.rangeClosed(1, 22).mapToObj(index -> ship("work-" + index, "Queued work " + index, PresenterFixtures.AT.plusSeconds(index)))
                .toList();
        ParkedTask parked = new ParkedTask(PresenterFixtures.heldHorn(), null, List.of());

        ConsoleSitrep sitrep = presented(new FleetSnapshot(List.of(), List.of(), List.of(parked), queued, List.of(), List.of()));

        assertThat(sitrep.chartedTotal()).isEqualTo(23);
        assertThat(sitrep.charted()).hasSize(SitrepConsolePresenter.CHARTED_SHOWN);
        assertThat(sitrep.charted()).first().extracting(ConsoleSitrep.Charted::task).isEqualTo("work-22");
        assertThat(presented(new FleetSnapshot(List.of(), List.of(), List.of(parked), List.of(), List.of(), List.of())).charted())
                .singleElement()
                .extracting(ConsoleSitrep.Charted::title, ConsoleSitrep.Charted::waiting)
                .containsExactly("Tune the horn", "deferred until 2026-11-01");
    }

    @Test
    void listsRecentlyLandedWorkWithItsDraftAndCountsItAll() {
        List<LandedTask> landed = IntStream.rangeClosed(1, 12).mapToObj(index -> landed("landed-" + index)).toList();

        ConsoleSitrep sitrep = presented(new FleetSnapshot(List.of(), List.of(), List.of(), List.of(), landed, List.of()));

        assertThat(sitrep.landedTotal()).isEqualTo(12);
        assertThat(sitrep.landed()).hasSize(SitrepConsolePresenter.LANDED_SHOWN);
        assertThat(sitrep.landed().getFirst().draft()).isEqualTo(DRAFT.url());
    }

    @Test
    void writesTheDocumentTheConsoleReads() {
        ConsoleSitrep sitrep = presented(new FleetSnapshot(List.of(new OpenDecision(PresenterFixtures.openDecision(), PresenterFixtures.captainItem())),
                List.of(), List.of(), List.of(), List.of(), List.of()));

        String json = new JsonMapper().writeValueAsString(sitrep);

        assertThat(json).contains("\"schema\":\"edi-sitrep.v1\"", "\"generated\":\"2026-10-15T10:30:00Z\"", "\"number\":\"D58\"",
                "\"recommended\":{\"key\":\"A\",\"why\":\"it is enough\"}", "\"landedTotal\":0", "\"chartedTotal\":0");
    }

    private ConsoleSitrep presented(FleetSnapshot fleet) {
        return presenter.present(fleet, NOW, "laptop", CaptainAddress.DEFAULT);
    }

    private static Task ship(String id, String title) {
        return ship(id, title, PresenterFixtures.AT);
    }

    private static Task ship(String id, String title, Instant filed) {
        return Task.queued(new TaskId(id), new Intake(TaskKind.SHIP, title, List.of(new ProjectName("harness")), null, null, List.of(), null), "", filed);
    }

    private static Worker workerOn(Task task) {
        return Worker.launched(WorkerId.first(task.id()), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), new WorkerWindow("edi", 1L, PresenterFixtures.AT, "@1", 2L),
                new WorkerLaunch(LaunchKind.DISPATCH, PresenterFixtures.AT, new BootId("boot")));
    }

    private static Delivery delivery(Task task) {
        return new Delivery(task.id(), DRAFT, true, "abc123", MergeRequestState.OPEN, null, NOW, NOW);
    }

    private static LandedTask landed(String id) {
        Task task = ship(id, "Landed " + id);
        return new LandedTask(task, delivery(task));
    }
}
