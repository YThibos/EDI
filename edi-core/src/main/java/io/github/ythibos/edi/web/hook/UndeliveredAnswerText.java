package io.github.ythibos.edi.web.hook;

import java.util.List;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.decision.UndeliveredAnswer;

final class UndeliveredAnswerText {

    private UndeliveredAnswerText() {
    }

    static String lines(List<UndeliveredAnswer> undelivered) {
        return undelivered.stream().map(UndeliveredAnswerText::line).collect(Collectors.joining("\n"));
    }

    private static String line(UndeliveredAnswer answer) {
        return answer.decision() + " is answered and the worker of " + answer.task() + " still waits on its question " + answer.question()
                + ": edi steer " + answer.task() + " --resolves " + answer.question();
    }
}
