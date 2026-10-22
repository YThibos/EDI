package io.github.ythibos.edi.web.task;

import static io.github.ythibos.edi.web.PresenterFixtures.AT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.task.Backlog;
import io.github.ythibos.edi.application.task.BacklogEntry;
import io.github.ythibos.edi.application.task.TaskDetail;
import io.github.ythibos.edi.application.task.TaskList;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.web.PresenterFixtures;

class TaskTextTest {

    @Test
    void listsTasksByStateWithTheirHolds() {
        TaskList list = new TaskList(List.of(TaskState.IN_FLIGHT, TaskState.QUEUED),
                List.of(PresenterFixtures.lantern(), PresenterFixtures.heldHorn(), PresenterFixtures.captainItem()));

        assertThat(new TaskListText().text(list)).isEqualTo("""
                In flight (0)
                Queued (3)
                  lantern      ship     harness + garden  HRN-3  Rework the lantern
                  fog-horn     ship     harness           -      Tune the horn [held]
                  lamp-choice  captain  -                 -      Choose the lamp
                """);
    }

    @Test
    void showsTheBacklogWithWhatHoldsOrBlocksEachTask() {
        Task horn = PresenterFixtures.heldHorn();
        Backlog backlog = new Backlog(false, List.of(
                new BacklogEntry(PresenterFixtures.lantern(), List.of()),
                new BacklogEntry(horn, List.of(new Blocker(new TaskId("lantern"), TaskState.QUEUED)))));

        assertThat(new BacklogText().text(backlog)).isEqualTo("""
                Queued (2)
                  lantern   ship  harness + garden  HRN-3  Rework the lantern
                  fog-horn  ship  harness           -      Tune the horn
                      held (deferral, until 2026-11-01): after the storm
                      blocked by lantern (queued)
                """);
        assertThat(new BacklogText().text(new Backlog(true, List.of()))).isEqualTo("Ready (0)\n");
    }

    @Test
    void showsAShipsDraftItsPipelineRunAndItsCost() {
        MergeRequestAddress address = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 7);
        Delivery draft = Delivery.of(PresenterFixtures.lantern().id(),
                new MergeRequestView(address, MergeRequestState.OPEN, true, "feature/HRN-3", "main", "1111", null, null), AT);
        TaskDetail detail = new TaskDetail(PresenterFixtures.lantern(), List.of(), null, draft, new PipelineRun("01RUN", "running", "ci", "1111", null),
                new TaskCost(PresenterFixtures.lantern().id(), new TokenUsage(1, 20, 3, 4), null, AT), List.of());

        assertThat(new TaskDetailText().text(detail)).contains("""
                Pipeline: run 01RUN running at ci
                Draft: https://github.com/example-owner/harness/pull/7 open, a draft (seen 2026-10-15 09:30Z)
                Cost: workers 28 tokens (1 in, 20 cache read, 3 cache write, 4 out); pipeline unknown
                """);
    }

    @Test
    void showsATaskWithItsDecisionNotesAndTimeline() {
        Task item = PresenterFixtures.captainItem()
                .noted("Ask before the season.")
                .heldForDecision(PresenterFixtures.openDecision().holdOnItsTask());
        TaskDetail detail = new TaskDetail(item, List.of(), PresenterFixtures.openDecision(), null, null, null,
                List.of(new RecordedEvent(1, AT, Actor.PRIMARY, "TaskAdded", item.id()), new RecordedEvent(2, AT, Actor.PRIMARY, "DecisionRaised", item.id())));

        assertThat(new TaskDetailText().text(detail)).isEqualTo("""
                lamp-choice  captain  queued
                Choose the lamp
                Projects: -
                Since: 2026-10-15
                Held (decision, until your word): D58 Which lamp? (since 2026-10-15 09:30Z)

                D58  lamp-choice  Which lamp?
                  Both work; one is simpler.
                  (A) The simple way
                  (B) The thorough way
                  Recommended: A - it is enough

                Notes
                  Ask before the season.

                Timeline
                  2026-10-15 09:30Z  primary  task added
                  2026-10-15 09:30Z  primary  decision raised
                """);
    }
}
