package io.github.ythibos.edi.domain.authority;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.TaskId;

public final class CaptainsAuthority {

    private CaptainsAuthority() {
    }

    public static Optional<CaptainsWord> onTask(TaskId task, Exchange exchange, List<Authorisation> words, List<CaptainAnswer> answers) {
        Optional<CaptainsWord> said = latest(words.stream().filter(word -> word.concerns(task)).filter(exchange::holds).toList());
        return said.or(() -> answers.stream()
                .filter(exchange::holds)
                .max(Comparator.comparing(CaptainAnswer::answeredAt))
                .map(CaptainAnswer::verbatim)
                .map(CaptainsWord::new));
    }

    public static Optional<CaptainsWord> onTheHome(Exchange exchange, List<Authorisation> words) {
        return latest(words.stream().filter(Authorisation::concernsTheHome).filter(exchange::holds).toList());
    }

    private static Optional<CaptainsWord> latest(List<Authorisation> words) {
        return words.stream().max(Comparator.comparing(Authorisation::saidAt)).map(Authorisation::verbatim).map(CaptainsWord::new);
    }
}
