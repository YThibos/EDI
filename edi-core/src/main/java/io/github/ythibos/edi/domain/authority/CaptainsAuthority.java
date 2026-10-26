package io.github.ythibos.edi.domain.authority;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.TaskId;

public final class CaptainsAuthority {

    private CaptainsAuthority() {
    }

    public static Optional<CaptainsWord> onTask(TaskId task, CaptainWordAct act, Exchange exchange, List<Authorisation> words,
            List<Decision> decisions) {
        return latest(heardOnTask(task, exchange, words).filter(Authorisation::isGeneral)).or(() -> decisions.stream()
                .filter(decision -> decision.authorises(act))
                .map(Decision::answer)
                .filter(exchange::holds)
                .max(Comparator.comparing(CaptainAnswer::answeredAt))
                .map(CaptainAnswer::verbatim)
                .map(CaptainsWord::new));
    }

    public static Optional<CaptainsWord> saidForActOnTask(TaskId task, CaptainWordAct act, Exchange exchange, List<Authorisation> words) {
        return latest(heardOnTask(task, exchange, words).filter(word -> word.isFor(act)));
    }

    public static Optional<CaptainsWord> onTheHome(Exchange exchange, List<Authorisation> words) {
        return latest(words.stream().filter(Authorisation::concernsTheHome).filter(exchange::holds));
    }

    private static Stream<Authorisation> heardOnTask(TaskId task, Exchange exchange, List<Authorisation> words) {
        return words.stream().filter(word -> word.concerns(task)).filter(exchange::holds);
    }

    private static Optional<CaptainsWord> latest(Stream<Authorisation> words) {
        return words.max(Comparator.comparing(Authorisation::saidAt)).map(Authorisation::verbatim).map(CaptainsWord::new);
    }
}
