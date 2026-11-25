package io.github.ythibos.edi.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.TaskId;

class CallKeyTest {

    @Test
    void readsEachCallAsTheSitrepNamesIt() {
        assertThat(CallKey.parse("D58")).isEqualTo(new CallKey.OfADecision(new DecisionNumber(58)));
        assertThat(CallKey.parse(" board:12 ")).isEqualTo(new CallKey.OfABoard(new BoardId(12)));
        assertThat(CallKey.parse("review:fit-lamp")).isEqualTo(new CallKey.OfAReview(new TaskId("fit-lamp")));
        assertThat(CallKey.parse("review:fit-lamp")).hasToString("review:fit-lamp");
        assertThat(CallKey.parse("board:12")).hasToString("board:12");
    }

    @Test
    void refusesAnythingElse() {
        assertThatThrownBy(() -> CallKey.parse("board:0")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A call on the captain's list is a D number, board:<number> or review:<task>, such as D58: board:0");
        assertThatThrownBy(() -> CallKey.parse("review:Fit Lamp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CallKey.parse("fit-lamp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CallKey.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
