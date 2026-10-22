package io.github.ythibos.edi.domain.board;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.TaskId;

public record Board(BoardId id, TaskId task, String file, String link, List<DecisionNumber> bound, int rounds, int openQuestions, Instant armedAt,
        Instant endedAt, Instant retiredAt) {

    public Board {
        Objects.requireNonNull(id, "A board is numbered.");
        Objects.requireNonNull(task, () -> "Board " + id + " belongs to the task whose worker armed it.");
        Objects.requireNonNull(file, () -> "Board " + id + " is a file Lavish serves.");
        Objects.requireNonNull(armedAt, () -> "Board " + id + " records when it was armed.");
        bound = List.copyOf(bound);
        if (rounds < 0) {
            throw new IllegalArgumentException("Board " + id + " counts its answered rounds from 0: " + rounds);
        }
        if (openQuestions < 0) {
            throw new IllegalArgumentException("Board " + id + " counts the questions waiting on the captain from 0: " + openQuestions);
        }
        if (openQuestions > 0 && link == null) {
            throw new IllegalArgumentException("Board " + id + " has questions waiting, so it names the link they are answered at.");
        }
    }

    public boolean isListening() {
        return endedAt == null && retiredAt == null;
    }

    public boolean waitsOnTheCaptain() {
        return isListening() && openQuestions > 0;
    }

    public Board posted(BoardRound round) {
        refuseUnlessListening();
        return new Board(id, task, file, round.link(), round.bound(), rounds, round.questions(), armedAt, endedAt, retiredAt);
    }

    public Board answeredOnceMore() {
        refuseUnlessListening();
        return new Board(id, task, file, link, bound, rounds + 1, 0, armedAt, endedAt, retiredAt);
    }

    public Board ended(Instant at) {
        refuseUnlessListening();
        return new Board(id, task, file, link, bound, rounds, openQuestions, armedAt, at, retiredAt);
    }

    public Board retired(Instant at) {
        return retiredAt == null ? new Board(id, task, file, link, bound, rounds, openQuestions, armedAt, endedAt, at) : this;
    }

    public List<BoardAnswer> answersIn(BoardFeedback.Answered feedback, Function<DecisionNumber, DecisionCard> cardOf, Instant at) {
        Map<DecisionNumber, BoardAnswer> latestWordPerDecision = new LinkedHashMap<>();
        feedback.prompts().forEach(prompt -> prompt.decisionNamed()
                .filter(bound::contains)
                .ifPresent(decision -> latestWordPerDecision.put(decision, BoardAnswer.received(id, rounds, decision, prompt, cardOf.apply(decision), at))));
        return List.copyOf(latestWordPerDecision.values());
    }

    public String listener() {
        return "edi:" + task + "#" + id;
    }

    private void refuseUnlessListening() {
        if (!isListening()) {
            throw new Refusal("Board " + id + " of " + task + " no longer listens: its session ended or its task was cleaned up.");
        }
    }
}
