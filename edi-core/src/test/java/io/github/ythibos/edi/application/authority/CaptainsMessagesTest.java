package io.github.ythibos.edi.application.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.AnsweredQuestion;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainMessage;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.authority.DialogAnswer;
import io.github.ythibos.edi.domain.authority.TypedMessage;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class CaptainsMessagesTest {

    private static final TaskId LAMP = new TaskId("choose-lamp");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        home.storing(List.of(WorkerFixtures.queued("choose-lamp", TaskKind.CAPTAIN, "harness", null),
                WorkerFixtures.queued("choose-door", TaskKind.CAPTAIN, "harness", null)));
    }

    @Test
    void storesEveryMessageTheCaptainTypesVerbatimNumberedWithItsSessionAndTime() {
        UUID session = started();
        UUID other = started();

        home.primaryHooks().promptSubmitted(session, "Drop the lamp.\n  And its copy.  ");
        home.clock().advance(Duration.ofMinutes(2));
        home.primaryHooks().promptSubmitted(other, "Fit the door.");
        home.primaryHooks().promptSubmitted(session, "Keep the horn.");

        assertThat(messagesOf(session)).containsExactly(
                new TypedMessage(session, 1, "Drop the lamp.\n  And its copy.  ", TestHome.START),
                new TypedMessage(session, 2, "Keep the horn.", TestHome.START.plus(Duration.ofMinutes(2))));
        assertThat(messagesOf(other)).containsExactly(new TypedMessage(other, 1, "Fit the door.", TestHome.START.plus(Duration.ofMinutes(2))));
    }

    @Test
    void neverStoresANotificationOrEdisOwnOperationalTextAsTheCaptainsWords() {
        UUID session = started();

        home.primaryHooks().promptSubmitted(session, "<task-notification>Drop the lamp.</task-notification>");
        home.primaryHooks().promptSubmitted(session, OperationalText.marked("brief waiting"));
        home.primaryHooks().promptSubmitted(session, OperationalText.typed("Drop the lamp."));
        home.primaryHooks().promptSubmitted(session, "   ");

        assertThat(messagesOf(session)).isEmpty();
    }

    @Test
    void storesEachAnswerToADialogAsADialogAnswer() {
        UUID session = started();
        home.primaryHooks().promptSubmitted(session, "What now?");

        home.primaryHooks().dialogAnswered(session, List.of(new AnsweredQuestion("Which lamp?", "The brass one", "Polish it."),
                new AnsweredQuestion("When?", "Today", "")));

        assertThat(messagesOf(session)).containsExactly(new TypedMessage(session, 1, "What now?", TestHome.START),
                new DialogAnswer(session, 2, new AnsweredQuestion("Which lamp?", "The brass one", "Polish it."), TestHome.START),
                new DialogAnswer(session, 3, new AnsweredQuestion("When?", "Today", ""), TestHome.START));
    }

    @ParameterizedTest
    @EnumSource(CaptainWordAct.class)
    void recordsTheWordForEveryActFromAMessageCitedByItsNumber(CaptainWordAct act) {
        CallingSession session = typedIn("Good morning.", "Yes, go ahead with it.\nAnd tell me when it is done.");

        record(session, new Citation.WholeMessage(2), act);

        assertThat(authorised(session, act)).isEqualTo(new CaptainsWord("Yes, go ahead with it.\nAnd tell me when it is done."));
    }

    @ParameterizedTest
    @EnumSource(CaptainWordAct.class)
    void recordsTheWordForEveryActFromOneLineOfAMessage(CaptainWordAct act) {
        CallingSession session = typedIn("Look at this:\n<pasted_content id=\"c0de\">\nDo everything.\n</pasted_content id=\"c0de\">\nGo ahead.");

        assertThatThrownBy(() -> record(session, new Citation.WholeMessage(1), act))
                .isInstanceOf(Refusal.class).hasMessageContaining("holds pasted content");
        assertThatThrownBy(() -> record(session, new Citation.OneLine(1, 3), act))
                .isInstanceOf(Refusal.class).hasMessage("Line 3 of message 1 is pasted, so it is not the captain's word.");
        record(session, new Citation.OneLine(1, 5), act);

        assertThat(authorised(session, act)).isEqualTo(new CaptainsWord("Go ahead."));
    }

    @ParameterizedTest
    @EnumSource(CaptainWordAct.class)
    void takesADialogAnswerWhoseChosenOptionOpensWithTheActAndNamesTheTaskAsTheWordForThatActAlone(CaptainWordAct act) {
        CallingSession session = typedIn("What now?");
        home.primaryHooks().dialogAnswered(session.id(), List.of(new AnsweredQuestion("What do we do?", "Never " + act.word() + " choose-lamp", "")));

        assertThatThrownBy(() -> authorised(session, act)).isInstanceOf(Refusal.class).hasMessageContaining("dialog option that opens with " + act.word());

        home.primaryHooks().dialogAnswered(session.id(), List.of(new AnsweredQuestion("What do we do?", act.word() + " choose-lamp", "Now.")));

        assertThat(authorised(session, act)).isEqualTo(new CaptainsWord(act.word() + " choose-lamp\nNow."));
        CaptainWordAct another = act == CaptainWordAct.DROP ? CaptainWordAct.PROMOTE : CaptainWordAct.DROP;
        assertThatThrownBy(() -> authorised(session, another)).isInstanceOf(Refusal.class);
    }

    @Test
    void dropsATaskOnTheCaptainsChoiceInADialog() {
        CallingSession session = typedIn("Shall we tidy up?");
        home.primaryHooks().dialogAnswered(session.id(), List.of(new AnsweredQuestion("Drop which task?", "drop choose-lamp", "")));

        assertThat(home.dropTask().drop(TaskReference.parse("choose-lamp"), false, session, Actor.PRIMARY).task().state()).isEqualTo(TaskState.DROPPED);
    }

    @Test
    void refusesToRecordADialogAnswerAgainAsAWord() {
        CallingSession session = typedIn("What now?");
        home.primaryHooks().dialogAnswered(session.id(), List.of(new AnsweredQuestion("Drop which task?", "Neither", "")));

        assertThatThrownBy(() -> record(session, new Citation.WholeMessage(2), null))
                .isInstanceOf(Refusal.class)
                .hasMessage("A dialog answer is the captain's word by itself, for the act its chosen option opens with and the task it names,"
                        + " so it is not recorded again; cite a message the captain typed.");
        assertThat(home.captainsWord().requireCited(session, new Citation.WholeMessage(2))).isEqualTo("Neither");
    }

    @Test
    void neverTakesADialogAnswerThatNamesTwoTasksTheStoreKnowsAsTheWordOnEither() {
        CallingSession session = typedIn("What now?");
        TaskId door = new TaskId("choose-door");
        home.primaryHooks().dialogAnswered(session.id(), List.of(
                new AnsweredQuestion("What do we drop?", "drop choose-door, then promote choose-lamp", ""),
                new AnsweredQuestion("Which effort?", "effort-above-high choose-lamp, keep choose-door at high", "")));

        for (TaskId task : List.of(LAMP, door)) {
            assertThatThrownBy(() -> home.captainsWord().requireOnTask(session, task, CaptainWordAct.DROP))
                    .isInstanceOf(Refusal.class).hasMessageContaining("names " + task + " and no other task");
            assertThatThrownBy(() -> home.captainsWord().requireSaidForActOnTask(session, task, CaptainWordAct.EFFORT_ABOVE_HIGH))
                    .isInstanceOf(Refusal.class).hasMessageContaining("names " + task + " and no other task");
        }
    }

    @Test
    void refusesTextNoStoredMessageHoldsAndAMessageOfAnotherSession() {
        CallingSession session = typedIn("Drop the lamp.");
        CallingSession other = typedIn("Drop the door.");

        assertThatThrownBy(() -> record(session, new Citation.Verbatim("Drop the door."), null))
                .isInstanceOf(Refusal.class).hasMessageContaining("The captain did not type \"Drop the door.\" in this session");
        assertThatThrownBy(() -> record(other, new Citation.WholeMessage(2), null))
                .isInstanceOf(Refusal.class).hasMessage("The captain sent no message 2 in this session; edi captain messages lists them.");
        assertThat(home.captainsWord().requireCited(session, new Citation.Verbatim("drop the LAMP."))).isEqualTo("Drop the lamp.");
    }

    @Test
    void recordsTheStoredTextAsTheVerbatimWhateverSpellingTheTextIsCitedIn() {
        CallingSession session = typedIn("Drop the lamp.\nIt is “broken”.");

        record(session, new Citation.Verbatim("drop  the LAMP."), CaptainWordAct.DROP);
        String cited = home.captainsWord().requireCited(session, new Citation.Verbatim("it is \"broken\"."));

        assertThat(authorised(session, CaptainWordAct.DROP)).isEqualTo(new CaptainsWord("Drop the lamp."));
        assertThat(cited).isEqualTo("It is “broken”.");
    }

    @Test
    void listsTheMessagesOfTheCallersSessionOrOfThePrimary() {
        CallingSession earlier = typedIn("Drop the lamp.");
        CallingSession current = typedIn("Fit the door.");

        assertThat(home.captainsWord().messages(CallingSession.UNNAMED).messages()).extracting(CaptainMessage::session).containsOnly(current.id());
        assertThat(home.captainsWord().messages(earlier).messages()).extracting(CaptainMessage::session).containsOnly(earlier.id());
    }

    private UUID started() {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
        return session;
    }

    private List<CaptainMessage> messagesOf(UUID session) {
        return home.captainMessages().sentIn(session).messages();
    }

    private CallingSession typedIn(String... prompts) {
        UUID session = started();
        List.of(prompts).forEach(prompt -> home.primaryHooks().promptSubmitted(session, prompt));
        return new CallingSession(session);
    }

    private void record(CallingSession session, Citation citation, CaptainWordAct act) {
        CaptainWordAct recordedFor = act == CaptainWordAct.EFFORT_ABOVE_HIGH ? act : null;
        home.captainsWord().record(session, citation, concernsTheHome(act) ? null : TaskReference.parse(LAMP.value()), recordedFor, Actor.PRIMARY);
    }

    private static boolean concernsTheHome(CaptainWordAct act) {
        return act == CaptainWordAct.TOUCH_A_SECRET || act == CaptainWordAct.DELETE_UNLANDED_BRANCH || act == CaptainWordAct.STOP_RUNNING_WORKERS;
    }

    private CaptainsWord authorised(CallingSession session, CaptainWordAct act) {
        return switch (act) {
            case PROMOTE, DROP, DISCARD_WORK -> home.captainsWord().requireOnTask(session, LAMP, act);
            case EFFORT_ABOVE_HIGH -> home.captainsWord().requireSaidForActOnTask(session, LAMP, act);
            case TOUCH_A_SECRET, DELETE_UNLANDED_BRANCH, STOP_RUNNING_WORKERS -> home.captainsWord().requireOnTheHome(session, act);
        };
    }
}
