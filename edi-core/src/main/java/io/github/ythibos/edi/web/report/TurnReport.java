package io.github.ythibos.edi.web.report;

import java.time.Instant;
import java.util.stream.Stream;

import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.WaitingBoard;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.application.fleet.WorkerQuestion;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionRound;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;
import io.github.ythibos.edi.domain.report.Turn;

public record TurnReport(WaitingOnTheCaptain waiting, Turn turn, ReportWidth width, UnreadableCalendar unreadableCalendar, Instant previousReportAt,
        DecisionRound gatheringRound) {

    public boolean holdsForTheGatheringRound(Decision decision) {
        return gatheringRound != null && gatheringRound.gathers(decision.number());
    }

    public boolean showsTheCardOf(OpenDecision open) {
        Decision decision = open.decision();
        return !DecisionRound.waitsForItsRound(decision, open.task()) && raisedSinceThePreviousReport(decision);
    }

    public boolean showsTheUnreadableCalendar() {
        return unreadableCalendar != null;
    }

    public boolean hasNothingForTheCaptain() {
        return !showsTheUnreadableCalendar() && waiting.hasNothingWaiting() && turn.done().isEmpty() && turn.next().isEmpty();
    }

    public boolean isNamedIn(Reply reply) {
        return everyWorkerQuestionIsEscalated() && namesEveryWaitingEntry(reply) && saysEveryRecordedItem(reply);
    }

    private boolean everyWorkerQuestionIsEscalated() {
        return waiting.workerQuestions().stream().allMatch(this::isEscalated);
    }

    private boolean isEscalated(WorkerQuestion question) {
        return question.worker().openQuestion().filter(asked -> openDecisions().anyMatch(open -> open.decision().isRaisedFor(asked))).isPresent();
    }

    private Stream<OpenDecision> openDecisions() {
        return Stream.concat(waiting.decisions().stream(), waiting.boards().stream().flatMap(board -> board.decisions().stream()));
    }

    private boolean namesEveryWaitingEntry(Reply reply) {
        return waiting.decisions().stream().allMatch(open -> reply.namesTheDecision(open.decision().number()))
                && waiting.boards().stream().allMatch(board -> namesTheBoard(board, reply))
                && waiting.reviews().stream().allMatch(ready -> reply.namesTheAddress(ready.delivery().address().url()));
    }

    private static boolean namesTheBoard(WaitingBoard board, Reply reply) {
        return reply.namesTheAddress(board.board().link()) && board.decisions().stream().allMatch(open -> reply.namesTheDecision(open.decision().number()));
    }

    private boolean saysEveryRecordedItem(Reply reply) {
        return Stream.concat(turn.done().stream(), turn.next().stream()).allMatch(item -> reply.saysTheItem(item.text()));
    }

    private boolean raisedSinceThePreviousReport(Decision decision) {
        return previousReportAt == null || decision.raisedAt().isAfter(previousReportAt);
    }
}
