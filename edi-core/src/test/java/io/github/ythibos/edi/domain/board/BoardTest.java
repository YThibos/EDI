package io.github.ythibos.edi.domain.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.task.TaskId;

class BoardTest {

    private static final Instant ARMED = Instant.parse("2026-10-17T09:00:00Z");
    private static final Instant ANSWERED = ARMED.plusSeconds(600);
    private static final DecisionNumber D58 = new DecisionNumber(58);
    private static final DecisionNumber D59 = new DecisionNumber(59);

    private final Board board = new Board(new BoardId(4), new TaskId("lantern-research"), "/home/user/.edi/data/tasks/lantern-research/board.html",
            List.of(D58, D59), 0, ARMED, null, null);

    @Test
    void readsAnAnswerFromTheContextDataTheBoardSent() {
        BoardPrompt choice = prompt("D58: option=A; note: LED", Map.of("decision", "D58", "option", "A"));

        assertThat(board.answeredOnceMore().answersIn(round(choice), ANSWERED))
                .containsExactly(new BoardAnswer(board.id(), 1, D58, "D58: option=A; note: LED", new OptionKey('A'), ANSWERED, null, null));
    }

    @Test
    void readsAnAnswerTheCaptainTypedTheWayHeAnswersInChat() {
        BoardPrompt typed = prompt("D59 B - and write down why", Map.of());
        BoardPrompt wordsOnly = prompt("D58: keep both until the spring", Map.of());

        assertThat(board.answeredOnceMore().answersIn(round(typed, wordsOnly), ANSWERED))
                .extracting(BoardAnswer::decision, BoardAnswer::option, BoardAnswer::verbatim)
                .containsExactly(
                        tuple(D59, new OptionKey('B'), "D59 B - and write down why"),
                        tuple(D58, null, "D58: keep both until the spring"));
    }

    @Test
    void ignoresDecisionsTheBoardWasNotArmedToAnswerAndPromptsThatNameNone() {
        BoardPrompt unbound = prompt("D60 A", Map.of());
        BoardPrompt annotation = prompt("The glow is too faint", Map.of());

        assertThat(board.answeredOnceMore().answersIn(round(unbound, annotation), ANSWERED)).isEmpty();
    }

    @Test
    void takesTheCaptainsLatestWordWhenARoundAnswersADecisionTwice() {
        BoardPrompt first = prompt("D58 A", Map.of());
        BoardPrompt changedMind = prompt("D58 B, on second thought", Map.of());

        assertThat(board.answeredOnceMore().answersIn(round(first, changedMind), ANSWERED))
                .singleElement()
                .satisfies(answer -> assertThat(answer.option()).isEqualTo(new OptionKey('B')));
    }

    @Test
    void countsItsRoundsAndRefusesMoreOnceItsSessionEnded() {
        Board ended = board.answeredOnceMore().answeredOnceMore().ended(ANSWERED);

        assertThat(ended.rounds()).isEqualTo(2);
        assertThat(ended.isListening()).isFalse();
        assertThatThrownBy(ended::answeredOnceMore).isInstanceOf(Refusal.class).hasMessageContaining("no longer listens");
        assertThat(ended.retired(ANSWERED).retiredAt()).isEqualTo(ANSWERED);
    }

    @Test
    void namesItsListenerAfterItsTaskAndNumber() {
        assertThat(board.listener()).isEqualTo("edi:lantern-research#4");
    }

    private static BoardPrompt prompt(String words, Map<String, String> context) {
        return new BoardPrompt("1", "choice", words, words, "section#s1 > form", context, List.of());
    }

    private static BoardFeedback.Answered round(BoardPrompt... prompts) {
        return new BoardFeedback.Answered(List.of(prompts), false);
    }
}
