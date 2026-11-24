package io.github.ythibos.edi.application.task;

import static io.github.ythibos.edi.testing.Intakes.ship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.decision.AnsweredDecision;
import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class HoldAndReleaseTest {

    private static final TaskReference FIT_LAMP = new TaskReference.ById(new TaskId("fit-lamp"));

    @TempDir
    private Path directory;

    @Test
    void holdsATaskForTheCaptainAndReleasesItAgain() throws IOException {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
        home.addTask().add(ship("fit-lamp", "Fit the lamp"), Actor.PRIMARY);

        Task held = home.holdTask().hold(FIT_LAMP, HoldKind.DEFERRAL, "after the storm", HoldUntil.day(LocalDate.of(2026, 11, 1)), Actor.PRIMARY);
        Task released = home.releaseTask().release(FIT_LAMP, Actor.PRIMARY);

        assertThat(held.reasonsItWaits()).isEqualTo("deferred: after the storm");
        assertThat(released.isHeld()).isFalse();
        assertThat(home.tasks().get(new TaskId("fit-lamp")).isHeld()).isFalse();
        assertThat(home.events().timelineOf(new TaskId("fit-lamp"))).extracting(RecordedEvent::type)
                .containsExactly("TaskAdded", "TaskHeld", "TaskReleased");
    }

    @Test
    void retiresAnAnsweredCaptainItemOnceTheReleaseLeavesItHoldingNothing() throws IOException {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
        home.addTask().add(Intakes.captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY);
        TaskReference lampChoice = new TaskReference.ById(new TaskId("lamp-choice"));
        home.holdTask().hold(lampChoice, HoldKind.DEFERRAL, "after the storm", HoldUntil.day(LocalDate.of(2026, 11, 1)), Actor.PRIMARY);
        home.raiseDecision().raise(lampChoice, Cards.card("Which lamp?"), Actor.PRIMARY);

        AnsweredDecision answered = home.answerDecision().answer(lampChoice, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
        Task released = home.releaseTask().release(lampChoice, Actor.PRIMARY);

        assertThat(answered.task().state()).isEqualTo(TaskState.QUEUED);
        assertThat(answered.task().reasonsItWaits()).isEqualTo("deferred: after the storm");
        assertThat(released.isDone()).isTrue();
        assertThat(home.tasks().get(new TaskId("lamp-choice")).isDone()).isTrue();
        assertThat(home.events().timelineOf(new TaskId("lamp-choice"))).extracting(RecordedEvent::type)
                .endsWith("DecisionAnswered", "TaskReleased", "StageChanged", "TaskDone");
    }

    @Test
    void keepsAReleasedCaptainItemQueuedUntilItsQuestionIsAnswered() throws IOException {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
        home.addTask().add(Intakes.captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY);
        TaskReference lampChoice = new TaskReference.ById(new TaskId("lamp-choice"));
        home.holdTask().hold(lampChoice, HoldKind.DEFERRAL, "after the storm", HoldUntil.day(LocalDate.of(2026, 11, 1)), Actor.PRIMARY);

        Task released = home.releaseTask().release(lampChoice, Actor.PRIMARY);

        assertThat(released.state()).isEqualTo(TaskState.QUEUED);
        assertThat(released.isHeld()).isFalse();
    }

    @Test
    void changesNothingWhenItRefuses() throws IOException {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
        home.addTask().add(ship("fit-lamp", "Fit the lamp"), Actor.PRIMARY);

        assertThatThrownBy(() -> home.releaseTask().release(FIT_LAMP, Actor.PRIMARY)).isInstanceOf(Refusal.class);
        assertThatThrownBy(() -> home.holdTask().hold(FIT_LAMP, HoldKind.DECISION, "decide", HoldUntil.captainsWord(), Actor.PRIMARY))
                .isInstanceOf(Refusal.class);

        assertThat(home.events().timelineOf(new TaskId("fit-lamp"))).extracting(RecordedEvent::type).containsExactly("TaskAdded");
    }

    @Test
    void notesATask() throws IOException {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
        home.addTask().add(ship("fit-lamp", "Fit the lamp"), Actor.PRIMARY);

        home.noteTask().note(FIT_LAMP, "  The ladder is ready.  ", Actor.PRIMARY);

        assertThat(home.tasks().get(new TaskId("fit-lamp")).notes()).isEqualTo("The ladder is ready.");
    }
}
