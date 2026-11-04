package io.github.ythibos.edi.web.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.board.AnsweredRounds;
import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.web.PresenterFixtures;

class BoardRoundsTextTest {

    private static final TaskId SCOUT = new TaskId("lamp-research");
    private static final BoardId BOARD = new BoardId(3);
    private static final Instant ANSWERED = PresenterFixtures.AT;

    private final BoardRoundsText text = new BoardRoundsText(PresenterFixtures.moments());

    @Test
    void printsEachRoundWithItsPromptsAndHowEachBoundAnswerSettled() {
        AnsweredRounds shown = new AnsweredRounds(SCOUT, List.of(
                new AnsweredRounds.Round(BOARD, "/tmp/board.html", 1, ANSWERED, "The captain answered on your research board /tmp/board.html, round 1.\n"
                        + "Prompt 1 (message): Brighter, please.", List.of()),
                new AnsweredRounds.Round(BOARD, "/tmp/board.html", 2, ANSWERED, "Prompt 3 (choice): D58: option=B", List.of(
                        new BoardAnswer(BOARD, 2, new DecisionNumber(58), "D58: option=B", new OptionKey('B'), ANSWERED, ANSWERED, null),
                        new BoardAnswer(BOARD, 2, new DecisionNumber(59), "The warm one", null, ANSWERED, null, null)))));

        assertThat(text.text(shown)).isEqualTo("""
                Board rounds answered on lamp-research (2):

                Board 3 (/tmp/board.html), round 1, answered %s:
                  The captain answered on your research board /tmp/board.html, round 1.
                  Prompt 1 (message): Brighter, please.

                Board 3 (/tmp/board.html), round 2, answered %s:
                  Prompt 3 (choice): D58: option=B
                  D58 option B: settled as the decision's answer
                  D59 in the captain's words: waiting to settle
                """.formatted(PresenterFixtures.moments().minute(ANSWERED), PresenterFixtures.moments().minute(ANSWERED)));
    }

    @Test
    void saysSoWhenNoBoardHasBeenAnswered() {
        assertThat(text.text(new AnsweredRounds(SCOUT, List.of()))).isEqualTo("No research board of lamp-research has been answered yet.\n");
    }
}
