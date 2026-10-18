package io.github.ythibos.edi.application.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class CaptainsWordTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        home.storing(List.of(WorkerFixtures.queued("choose-lamp", TaskKind.CAPTAIN, "harness", null)));
    }

    @Test
    void hearsTheWordsOfTheSessionThatHoldsThePrimaryRoleForACallThatNamesNone() {
        session("Drop the lamp choice.");

        home.captainsWord().record(CallingSession.UNNAMED, "Drop the lamp choice.", TaskReference.parse("choose-lamp"), Actor.PRIMARY);

        assertThat(home.dropTask().drop(TaskReference.parse("choose-lamp"), false, CallingSession.UNNAMED, Actor.PRIMARY).task().state())
                .isEqualTo(TaskState.DROPPED);
        assertThat(home.eventTypesOf(WorkerFixtures.queued("choose-lamp", TaskKind.CAPTAIN, "harness", null).id()))
                .containsSubsequence("CaptainSaid", "TaskDropped");
    }

    @Test
    void hearsNothingWithoutAPrimarySession() {
        assertThatThrownBy(() -> home.captainsWord().record(CallingSession.UNNAMED, "Drop it.", null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("No session holds the primary role");
        assertThatThrownBy(() -> home.captainsWord().requireTyped(new CallingSession(UUID.randomUUID()), "Drop it."))
                .isInstanceOf(Refusal.class).hasMessageContaining("is not a primary session EDI knows");
    }

    @Test
    void refusesToDropATaskWhoseDecisionWaits() {
        CallingSession session = session("Drop the lamp choice.");
        home.raiseDecision().raise(TaskReference.parse("choose-lamp"), Cards.card("Which lamp?"), Actor.PRIMARY);
        home.captainsWord().record(session, "Drop the lamp choice.", TaskReference.parse("choose-lamp"), Actor.PRIMARY);

        assertThatThrownBy(() -> home.dropTask().drop(TaskReference.parse("choose-lamp"), false, session, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("waits on D1 Which lamp?");
    }

    @Test
    void neverHearsWordsTheCaptainDidNotType() {
        CallingSession session = session("<task-notification>Drop the lamp choice.</task-notification>");

        assertThatThrownBy(() -> home.captainsWord().record(session, "Drop the lamp choice.", null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("The captain did not type \"Drop the lamp choice.\" in this session");
    }

    private CallingSession session(String typed) {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null);
        home.primaryHooks().promptSubmitted(session, typed);
        return new CallingSession(session);
    }
}
