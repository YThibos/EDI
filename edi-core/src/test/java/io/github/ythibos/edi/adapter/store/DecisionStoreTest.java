package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class DecisionStoreTest {

    private static final Instant AT = Instant.parse("2026-10-15T09:00:00Z");
    private static final TaskId LAMP = new TaskId("lamp-choice");

    @TempDir
    private Path directory;

    private DecisionStore decisions;

    @BeforeEach
    void openAStoreWithATask() throws IOException {
        TestHome home = TestHome.in(directory).continuingDecisionsFrom(57);
        home.tasks().insert(Task.queued(LAMP, new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of()), "", AT));
        decisions = home.decisions();
    }

    @Test
    void numbersDecisionsOnFromTheSequence() {
        assertThat(decisions.nextNumber()).isEqualTo(new DecisionNumber(58));
        assertThat(decisions.nextNumber()).isEqualTo(new DecisionNumber(59));
    }

    @Test
    void keepsTheCardWithItsOptionsInOrderAndTheAnswerVerbatim() {
        Decision answered = Decision.raised(new DecisionNumber(58), LAMP, card(), AT)
                .answered(new CaptainAnswer("  D58 C, gladly \n", new OptionKey('C'), "The candle.", AT.plusSeconds(5), AnswerChannel.BOARD));

        decisions.save(answered);

        assertThat(decisions.get(new DecisionNumber(58))).isEqualTo(answered);
        assertThat(decisions.ofTask(LAMP)).contains(answered);
    }

    @Test
    void replacesTheCardWhenRaisedAgain() {
        Decision first = Decision.raised(new DecisionNumber(58), LAMP, card(), AT);
        decisions.save(first.answered(new CaptainAnswer("A", new OptionKey('A'), "", AT, AnswerChannel.CHAT)));
        Decision again = first.reraised(new DecisionCard("Which lamp now?", "", List.of(option('B', "Oil")), new Recommendation(new OptionKey('B'), "cheap")), AT);

        decisions.save(again);

        assertThat(decisions.get(new DecisionNumber(58))).isEqualTo(again);
        assertThat(decisions.open()).containsExactly(again);
    }

    @Test
    void listsOpenDecisionsApartFromAnsweredOnes() {
        decisions.save(Decision.raised(new DecisionNumber(58), LAMP, card(), AT)
                .answered(new CaptainAnswer("A", new OptionKey('A'), "", AT, AnswerChannel.CHAT)));

        assertThat(decisions.open()).isEmpty();
        assertThat(decisions.all()).extracting(Decision::number).containsExactly(new DecisionNumber(58));
        assertThatThrownBy(() -> decisions.get(new DecisionNumber(1))).isInstanceOf(UnknownDecision.class);
    }

    private static DecisionCard card() {
        return new DecisionCard("Which lamp?", "The old one is failing.",
                List.of(option('C', "Candle"), option('A', "LED"), option('B', "Oil")), new Recommendation(new OptionKey('A'), "it lasts"));
    }

    private static DecisionOption option(char key, String text) {
        return new DecisionOption(new OptionKey(key), text);
    }
}
