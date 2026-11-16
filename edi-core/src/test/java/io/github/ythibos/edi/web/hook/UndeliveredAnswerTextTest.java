package io.github.ythibos.edi.web.hook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.UndeliveredAnswer;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.TaskId;

class UndeliveredAnswerTextTest {

    @Test
    void namesTheSteerThatCarriesEachAnswerOnItsOwnLine() {
        List<UndeliveredAnswer> undelivered = List.of(new UndeliveredAnswer(new DecisionNumber(7), new TaskId("fit-lamp"), new StatusKey("lamp-choice")),
                new UndeliveredAnswer(new DecisionNumber(9), new TaskId("hang-bell"), new StatusKey("nm-4-review")));

        assertThat(UndeliveredAnswerText.lines(undelivered)).isEqualTo("""
                D7 is answered and the worker of fit-lamp still waits on its question lamp-choice: edi steer fit-lamp --resolves lamp-choice
                D9 is answered and the worker of hang-bell still waits on its question nm-4-review: edi steer hang-bell --resolves nm-4-review""");
    }
}
