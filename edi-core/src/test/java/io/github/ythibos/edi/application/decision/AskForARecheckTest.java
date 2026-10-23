package io.github.ythibos.edi.application.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class AskForARecheckTest {

    private static final TaskId LAMP_CHOICE = new TaskId("choose-lamp");
    private static final TaskId FIT_LAMP = new TaskId("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;
    private DecisionNumber decision;

    @BeforeEach
    void raiseADecision() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        home.storing(List.of(WorkerFixtures.queued(LAMP_CHOICE.value(), TaskKind.CAPTAIN, "harness", null)));
        decision = home.raiseDecision().raise(new TaskReference.ById(LAMP_CHOICE), Cards.card("Which lamp?"), Actor.PRIMARY);
    }

    @Test
    void wakesThePrimaryToReCheckAnOpenDecisionWithoutAnsweringIt() {
        home.askForARecheck().ask(new TaskReference.ByDecision(decision), "the lamp came in", Actor.CAPTAIN);

        assertThat(home.decisions().get(decision).isOpen()).isTrue();
        assertThat(home.eventTypesOf(LAMP_CHOICE)).endsWith("RecheckAsked");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind(), queued -> queued.wake().detail())
                .contains(tuple(WakeKind.RECHECK_ASKED, decision + " (\"the lamp came in\")"));
    }

    @Test
    void refusesAReCheckOfAnAnsweredDecision() {
        home.answerDecision().answer(new TaskReference.ByDecision(decision), new GivenAnswer(decision + " A", new OptionKey('A'), "", AnswerChannel.CHAT),
                Actor.PRIMARY);

        assertThatThrownBy(() -> home.askForARecheck().ask(new TaskReference.ByDecision(decision), "", Actor.CAPTAIN))
                .isInstanceOf(Refusal.class)
                .hasMessage(decision + " is already answered: " + decision + " A");
    }

    @Test
    void refusesAReCheckOfAnAnsweredDecisionInsteadOfReCheckingTheLaterOneOnItsTask() {
        home.storing(List.of(WorkerFixtures.queuedShip(FIT_LAMP.value(), "harness", "HRN-3")));
        TaskReference fitLamp = new TaskReference.ById(FIT_LAMP);
        DecisionNumber earlier = home.raiseDecision().raise(fitLamp, Cards.card("Which ladder?"), Actor.PRIMARY);
        home.answerDecision().answer(new TaskReference.ByDecision(earlier), new GivenAnswer(earlier + " A", new OptionKey('A'), "", AnswerChannel.CHAT),
                Actor.PRIMARY);
        DecisionNumber later = home.raiseDecision().raise(fitLamp, Cards.card("Which bulb?"), Actor.PRIMARY);

        assertThatThrownBy(() -> home.askForARecheck().ask(new TaskReference.ByDecision(earlier), "", Actor.CAPTAIN))
                .isInstanceOf(Refusal.class)
                .hasMessage(earlier + " is already answered: " + earlier + " A");
        assertThat(home.askForARecheck().ask(new TaskReference.ByDecision(later), "", Actor.CAPTAIN).number()).isEqualTo(later);
    }
}
