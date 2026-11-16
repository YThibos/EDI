package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.task.UnknownTask;
import io.github.ythibos.edi.domain.task.Wait;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.Stages;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class TaskStoreTest {

    private static final Instant AT = Instant.parse("2026-10-15T09:00:00.123Z");

    @TempDir
    private Path directory;

    private TaskStore tasks;
    private DecisionStore decisions;

    @BeforeEach
    void openAStore() throws IOException {
        TestHome home = TestHome.in(directory);
        tasks = home.tasks();
        decisions = home.decisions();
    }

    @Test
    void keepsEveryPartOfATask() {
        tasks.insert(task("lantern", List.of()));
        Task stored = task("fog-horn", List.of(new TaskId("lantern")))
                .noted("Tune it to the old pitch.")
                .held(new CaptainHold(HoldKind.APPROVAL, "needs the keeper's yes", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT));

        tasks.insert(stored);

        assertThat(tasks.get(new TaskId("fog-horn"))).isEqualTo(stored);
    }

    @Test
    void keepsTheChangesToAState() {
        Task captainItem = Task.queued(new TaskId("lamp-choice"), new Intake(TaskKind.CAPTAIN, "Choose", List.of(), null, null, List.of(), null), "", AT);
        tasks.insert(captainItem);
        CaptainHold decisionHold = new CaptainHold(HoldKind.DECISION, "D1 Which lamp?", HoldUntil.captainsWord(), AT);

        tasks.update(captainItem.heldForDecision(decisionHold));
        Task held = tasks.get(captainItem.id());
        tasks.update(Stages.answered(held, AT.plusSeconds(60)));

        assertThat(held.hold()).isEqualTo(decisionHold);
        assertThat(tasks.get(captainItem.id()).state()).isEqualTo(TaskState.DONE);
        assertThat(tasks.get(captainItem.id()).closed()).isEqualTo(AT.plusSeconds(60));
        assertThat(tasks.get(captainItem.id()).isHeld()).isFalse();
    }

    @Test
    void keepsADroppedTaskWithItsClosingTime() {
        Task held = task("lantern", List.of())
                .held(new CaptainHold(HoldKind.DEFERRAL, "after the season", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT));
        tasks.insert(held);

        tasks.update(Stages.dropped(held, new CaptainsWord("Drop the lantern."), AT.plusSeconds(60)));

        assertThat(tasks.inState(TaskState.DROPPED)).singleElement().satisfies(dropped -> {
            assertThat(dropped.closed()).isEqualTo(AT.plusSeconds(60));
            assertThat(dropped.isHeld()).isFalse();
        });
    }

    @Test
    void listsAStateInQueueOrder() {
        List.of("third-in-name-first-in-line", "a-second", "b-last").forEach(id -> tasks.insert(task(id, List.of())));

        assertThat(tasks.inState(TaskState.QUEUED)).extracting(task -> task.id().value())
                .containsExactly("third-in-name-first-in-line", "a-second", "b-last");
        assertThat(tasks.inState(TaskState.DONE)).isEmpty();
    }

    @Test
    void findsATaskByTheNumberOfItsDecision() {
        tasks.insert(task("lantern", List.of()));
        decisions.save(Decision.raised(new DecisionNumber(7), new TaskId("lantern"), Cards.card("Which lamp?"), AT));

        assertThat(tasks.get(new TaskReference.ByDecision(new DecisionNumber(7))).id()).isEqualTo(new TaskId("lantern"));
        assertThatThrownBy(() -> tasks.get(new TaskReference.ByDecision(new DecisionNumber(8)))).isInstanceOf(UnknownDecision.class);
        assertThatThrownBy(() -> tasks.get(new TaskReference.ById(new TaskId("nowhere")))).isInstanceOf(UnknownTask.class);
    }

    @Test
    void tellsWhereEachBlockerStands() {
        tasks.insert(task("lantern", List.of()));
        tasks.insert(task("mast", List.of()));
        Task blocked = task("fog-horn", List.of(new TaskId("mast"), new TaskId("lantern")));
        tasks.insert(blocked);

        assertThat(tasks.waitsOf(blocked)).containsExactly(
                Wait.afterLanded(new TaskId("mast"), TaskStage.QUEUED), Wait.afterLanded(new TaskId("lantern"), TaskStage.QUEUED));
        assertThat(tasks.contains(new TaskId("mast"))).isTrue();
        assertThat(tasks.contains(new TaskId("nowhere"))).isFalse();
    }

    private static Task task(String id, List<TaskId> blockedBy) {
        Intake intake = new Intake(TaskKind.SHIP, "Work on " + id, List.of(new ProjectName("harness"), new ProjectName("garden")),
                WorkKey.parse("HRN-4"), new BaseBranch("develop"), blockedBy, null);
        return Task.queued(new TaskId(id), intake, "Notes for " + id + ".", AT);
    }
}
