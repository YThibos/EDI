package io.github.ythibos.edi.web.report;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.github.ythibos.edi.application.fleet.ChangingRequest;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.WaitingBoard;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.decision.Decision;

final class WaitingEntries {

    private WaitingEntries() {
    }

    static List<DecisionZone.Entry> of(WaitingOnTheCaptain waiting, TurnWording wording, Predicate<Decision> showsTheCard) {
        return Stream.of(
                        waiting.decisions().stream().map(open -> call(open, wording, showsTheCard)),
                        waiting.boards().stream().map(board -> boardQuestions(board, wording)),
                        waiting.reviews().stream().map(ready -> review(ready, wording)),
                        waiting.changing().stream().map(changing -> changing(changing, wording)))
                .flatMap(Function.identity())
                .toList();
    }

    private static DecisionZone.Entry call(OpenDecision open, TurnWording wording, Predicate<Decision> showsTheCard) {
        Decision decision = open.decision();
        ProjectSet projects = wording.projectsOf(open.task());
        return showsTheCard.test(decision)
                ? new DecisionZone.Call(decision.number(), projects, decision.card())
                : new DecisionZone.Reminder(decision.number(), projects, decision.card());
    }

    private static DecisionZone.Entry boardQuestions(WaitingBoard waiting, TurnWording wording) {
        Board board = waiting.board();
        return new DecisionZone.BoardQuestions(wording.projectsOf(waiting.task()), wording.textOf(waiting.task()), board.openQuestions(), board.link(),
                waiting.decisions().stream().map(WaitingEntries::boardDecision).toList());
    }

    private static DecisionZone.BoardDecision boardDecision(OpenDecision open) {
        return new DecisionZone.BoardDecision(open.decision().number(), open.decision().card().title());
    }

    private static DecisionZone.Entry review(ReadyForReview ready, TurnWording wording) {
        return new DecisionZone.Review(wording.projectsOf(ready.task()), wording.textOf(ready.task()), ready.delivery().address().url());
    }

    private static DecisionZone.Entry changing(ChangingRequest changing, TurnWording wording) {
        return new DecisionZone.Changing(wording.projectsOf(changing.task()), wording.textOf(changing.task()), changing.delivery().address().url(),
                changing.whyChanging());
    }
}
