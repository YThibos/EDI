package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.MachineFixture;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class RemindOfUnansweredQuestionsTest {

    private static final StatusKey SOCKET = new StatusKey("socket");

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker lamp;

    @BeforeEach
    void askAQuestion() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        lamp = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        home.recordStatus().report(lamp.id(), StatusState.NEEDS_DECISION, SOCKET, null, "Which socket does the lamp take?");
    }

    @Test
    void raisesAnUnansweredQuestionAgainEveryTenMinutesNamingTheWorkerAndTheKey() {
        home.clock().advance(Duration.ofMinutes(9));
        remind();
        List<Wake> beforeTheInterval = reminders();
        home.clock().advance(Duration.ofMinutes(1));
        remind();
        remind();
        List<Wake> atTheInterval = reminders();
        home.clock().advance(Duration.ofMinutes(10));
        remind();

        assertThat(beforeTheInterval).isEmpty();
        assertThat(atTheInterval).singleElement().satisfies(wake -> {
            assertThat(wake.worker()).isEqualTo(lamp.id());
            assertThat(wake.detail()).isEqualTo("(socket) Which socket does the lamp take?");
            assertThat(wake.moment()).isEqualTo(TestHome.START);
        });
        assertThat(reminders()).hasSize(2);
    }

    @Test
    void stopsTheMomentASteerThatResolvesTheKeyIsRecorded() {
        home.clock().advance(Duration.ofMinutes(10));
        remind();
        home.steerWorker().steer(new TaskReference.ById(lamp.id().task()), "The bayonet socket.", SOCKET, Actor.PRIMARY);
        home.clock().advance(Duration.ofMinutes(30));
        remind();

        assertThat(reminders()).hasSize(1);
    }

    @Test
    void keepsRemindingWhileOnlyASteerForAnotherKeyIsRecorded() {
        home.steerWorker().steer(new TaskReference.ById(lamp.id().task()), "Mind the bulb too.", new StatusKey("bulb"), Actor.PRIMARY);
        home.clock().advance(Duration.ofMinutes(10));
        remind();

        assertThat(reminders()).hasSize(1);
    }

    @Test
    void stopsWhenTheWorkerReportsAnotherState() {
        home.clock().advance(Duration.ofMinutes(5));
        home.recordStatus().report(lamp.id(), StatusState.WORKING, null, null, "Trying the bayonet socket.");
        home.clock().advance(Duration.ofMinutes(30));
        remind();

        assertThat(reminders()).isEmpty();
    }

    @Test
    void waitsWhileTheCaptainIsAwayAndResumesOnTheReturnButRemindsInQuiet() {
        home.changePresence().away("Carry on with the lamp while I am out.");
        home.clock().advance(Duration.ofMinutes(30));
        remind();
        List<Wake> whileAway = reminders();
        home.changePresence().quiet();
        remind();
        List<Wake> inQuiet = reminders();
        home.clock().advance(Duration.ofMinutes(10));
        home.changePresence().present();
        remind();

        assertThat(whileAway).isEmpty();
        assertThat(inQuiet).hasSize(1);
        assertThat(reminders()).hasSize(2);
    }

    @Test
    void followsTheIntervalTheMachineProfileSets() throws IOException {
        home.writing("config/machine.yaml", MachineFixture.current().profile() + "supervision:\n  questionReminder: PT3M\n");
        home.clock().advance(Duration.ofMinutes(3));
        remind();

        assertThat(reminders()).hasSize(1);
    }

    @Test
    void listsEachOpenQuestionWithItsAgeUntilTheResolvingSteerIsAcknowledged() {
        home.clock().advance(Duration.ofMinutes(25));
        OpenWorkerQuestions asked = home.openWorkerQuestions().open();
        home.steerWorker().steer(new TaskReference.ById(lamp.id().task()), "The bayonet socket.", SOCKET, Actor.PRIMARY);
        home.workerInbox().read(lamp.id());
        home.workerInbox().acknowledge(lamp.id(), 1);

        assertThat(asked.questions()).singleElement().satisfies(question -> {
            assertThat(question.worker()).isEqualTo(lamp.id());
            assertThat(question.question().key()).isEqualTo(SOCKET);
            assertThat(question.waited()).isEqualTo(Duration.ofMinutes(25));
        });
        assertThat(home.openWorkerQuestions().open().questions()).isEmpty();
    }

    private void remind() {
        home.remindOfUnansweredQuestions().remind();
    }

    private List<Wake> reminders() {
        return home.wakes().unacknowledged().stream().map(queued -> queued.wake()).filter(wake -> wake.kind() == WakeKind.QUESTION_UNANSWERED).toList();
    }
}
