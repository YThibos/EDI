package io.github.ythibos.edi.domain.authority;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.TaskId;

public final class CaptainsAuthority {

    private CaptainsAuthority() {
    }

    public static Optional<CaptainsWord> onTask(TaskId task, CaptainWordAct act, Exchange exchange, Heard heard, List<Decision> decisions) {
        return latest(Stream.concat(
                        heardOnTask(task, exchange, heard.words()).filter(Authorisation::isGeneral).map(CaptainsAuthority::said),
                        chosenInADialog(exchange, heard.answers(), answer -> answer.authorises(act, task))))
                .or(() -> answeredDecision(act, exchange, decisions));
    }

    public static Optional<CaptainsWord> saidForActOnTask(TaskId task, CaptainWordAct act, Exchange exchange, Heard heard) {
        return latest(Stream.concat(
                heardOnTask(task, exchange, heard.words()).filter(word -> word.isFor(act)).map(CaptainsAuthority::said),
                chosenInADialog(exchange, heard.answers(), answer -> answer.authorises(act, task))));
    }

    public static Optional<CaptainsWord> onTheHome(CaptainWordAct act, Exchange exchange, Heard heard) {
        return latest(Stream.concat(
                heard.words().stream().filter(Authorisation::concernsTheHome).filter(exchange::holds).map(CaptainsAuthority::said),
                chosenInADialog(exchange, heard.answers(), answer -> answer.authorisesOnTheHome(act))));
    }

    private static Stream<Authorisation> heardOnTask(TaskId task, Exchange exchange, List<Authorisation> words) {
        return words.stream().filter(word -> word.concerns(task)).filter(exchange::holds);
    }

    private static Given said(Authorisation word) {
        return new Given(word.saidAt(), word.verbatim());
    }

    private static Stream<Given> chosenInADialog(Exchange exchange, List<DialogAnswer> answers, Predicate<DialogAnswer> authorising) {
        return answers.stream().filter(exchange::holds).filter(authorising).map(answer -> new Given(answer.sentAt(), answer.wholeText()));
    }

    private static Optional<CaptainsWord> latest(Stream<Given> given) {
        return given.max(Comparator.comparing(Given::at)).map(Given::verbatim).map(CaptainsWord::new);
    }

    private static Optional<CaptainsWord> answeredDecision(CaptainWordAct act, Exchange exchange, List<Decision> decisions) {
        return decisions.stream()
                .filter(decision -> decision.authorises(act))
                .map(Decision::answer)
                .filter(exchange::holds)
                .max(Comparator.comparing(CaptainAnswer::answeredAt))
                .map(CaptainAnswer::verbatim)
                .map(CaptainsWord::new);
    }

    public record Heard(List<Authorisation> words, List<DialogAnswer> answers) {
    }

    private record Given(Instant at, String verbatim) {
    }
}
