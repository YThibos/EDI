package io.github.ythibos.edi.domain.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;

class DecisionTest {

    private static final Instant AT = Instant.parse("2026-10-15T09:00:00Z");
    private static final DecisionNumber D58 = new DecisionNumber(58);
    private static final OptionKey A = new OptionKey('A');
    private static final OptionKey B = new OptionKey('B');

    @Test
    void opensWhenRaisedAndHoldsItsTaskUntilTheCaptainsWord() {
        Decision decision = Decision.raised(D58, new TaskId("lamp-choice"), card("Which lamp?"), AT);

        assertThat(decision.isOpen()).isTrue();
        assertThat(decision.holdOnItsTask().kind()).isEqualTo(HoldKind.DECISION);
        assertThat(decision.holdOnItsTask().reason()).isEqualTo("D58 Which lamp?");
        assertThat(decision.holdOnItsTask().until()).isEqualTo(HoldUntil.captainsWord());
    }

    @Test
    void keepsItsNumberWhenRaisedAgainWhileOpen() {
        Decision open = Decision.raised(D58, new TaskId("lamp-choice"), card("Which lamp?"), AT);

        Decision again = open.reraised(card("Which lamp, now?"), AT.plusSeconds(60));

        assertThat(again.number()).isEqualTo(D58);
        assertThat(again.isOpen()).isTrue();
        assertThat(again.card().title()).isEqualTo("Which lamp, now?");
    }

    @Test
    void neverRewritesTheCardTheCaptainAnswered() {
        Decision answered = Decision.raised(D58, new TaskId("lamp-choice"), card("Which lamp?"), AT).answered(answer("D58 A", A));

        assertThatThrownBy(() -> answered.reraised(card("Which lamp, now?"), AT.plusSeconds(60))).isInstanceOf(Refusal.class)
                .hasMessage("D58 is already answered: D58 A; a new card on lamp-choice is a new decision with its own number.");
    }

    @Test
    void takesOneAnswer() {
        Decision answered = Decision.raised(D58, new TaskId("lamp-choice"), card("Which lamp?"), AT).answered(answer("D58 A", A));

        assertThat(answered.isOpen()).isFalse();
        assertThatThrownBy(() -> answered.answered(answer("again", B))).isInstanceOf(Refusal.class).hasMessage("D58 is already answered: D58 A");
    }

    @Test
    void takesOnlyAnOptionItsCardOffers() {
        Decision open = Decision.raised(D58, new TaskId("lamp-choice"), card("Which lamp?"), AT);

        assertThatIllegalArgumentException().isThrownBy(() -> open.answered(answer("D58 C", new OptionKey('C')))).withMessage("D58 offers no option C.");
        assertThat(open.answered(answer("later, after the storm", null)).answer().choosesAnOption()).isFalse();
    }

    @Test
    void readsDecisionNumbersAndOptionKeys() {
        assertThat(DecisionNumber.parse("D58")).isEqualTo(D58);
        assertThat(D58).hasToString("D58");
        assertThat(DecisionNumber.isWritten("D")).isFalse();
        assertThat(DecisionNumber.isWritten("d58")).isFalse();
        assertThat(DecisionNumber.isWritten("D5x")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionNumber(0));
        assertThat(OptionKey.parse("B")).isEqualTo(B);
        assertThatIllegalArgumentException().isThrownBy(() -> OptionKey.parse("AB"));
        assertThatIllegalArgumentException().isThrownBy(() -> OptionKey.parse("a"));
    }

    @Test
    void readsAReferenceAsADecisionNumberOrATaskId() {
        assertThat(TaskReference.parse("D58")).isEqualTo(new TaskReference.ByDecision(D58));
        assertThat(TaskReference.parse("lamp-choice")).isEqualTo(new TaskReference.ById(new TaskId("lamp-choice")));
    }

    @Test
    void needsACompleteCard() {
        List<DecisionOption> options = List.of(new DecisionOption(A, "LED"), new DecisionOption(B, "Oil"));
        Recommendation recommendA = new Recommendation(A, "lasts longer");

        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionCard(" ", "", options, recommendA));
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionCard("Lamp?", "", List.of(), recommendA));
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionCard("Lamp?", "", List.of(options.getFirst(), options.getFirst()), recommendA));
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionCard("Lamp?", "", options, new Recommendation(new OptionKey('C'), "why")))
                .withMessage("The card recommends option C, which it does not offer.");
        assertThatIllegalArgumentException().isThrownBy(() -> new Recommendation(A, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionOption(A, ""));
    }

    @Test
    void recordsTheCaptainsWordsExactly() {
        assertThat(answer("  D58,  A \n", A).verbatim()).isEqualTo("  D58,  A \n");
        assertThatIllegalArgumentException().isThrownBy(() -> answer(" \n ", A));
    }

    private static DecisionCard card(String title) {
        return new DecisionCard(title, "", List.of(new DecisionOption(A, "LED"), new DecisionOption(B, "Oil")), new Recommendation(A, "lasts longer"));
    }

    private static CaptainAnswer answer(String verbatim, OptionKey option) {
        return new CaptainAnswer(verbatim, option, "", AT, AnswerChannel.CHAT);
    }
}
