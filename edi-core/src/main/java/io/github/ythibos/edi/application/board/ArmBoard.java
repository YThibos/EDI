package io.github.ythibos.edi.application.board;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardBinding;
import io.github.ythibos.edi.domain.board.BoardCard;
import io.github.ythibos.edi.domain.board.BoardRound;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardArmed;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class ArmBoard {

    private static final String HTML = ".html";

    private final EdiHome home;
    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final BoardStore boards;
    private final EventLog events;
    private final RaiseDecision raiseDecision;
    private final BoardListeners listeners;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ArmBoard(EdiHome home, TaskStore tasks, DecisionStore decisions, BoardStore boards, EventLog events, RaiseDecision raiseDecision,
            BoardListeners listeners, Clock clock, TransactionTemplate transaction) {
        this.home = home;
        this.tasks = tasks;
        this.decisions = decisions;
        this.boards = boards;
        this.events = events;
        this.raiseDecision = raiseDecision;
        this.listeners = listeners;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Board arm(WorkerId worker, TaskReference reference, Path file, BoardRound round) {
        Board armed = transaction.execute(_ -> armedOrPostedAgain(worker, tasks.get(reference), file, round));
        listeners.listenTo(armed);
        return armed;
    }

    private Board armedOrPostedAgain(WorkerId worker, Task task, Path file, BoardRound round) {
        Instant now = clock.now();
        requireItsOwnWorker(task, worker);
        String board = boardFileOf(task, file).toString();
        round.bound().forEach(this::requireOpen);
        Optional<Board> listening = boards.listeningAt(board);
        BoardRound raised = withItsCardRaised(round, task, worker, listening);
        Board armed = listening
                .map(earlier -> postedAgain(earlier, task, raised))
                .orElseGet(() -> boards.arm(task.id(), board, raised, now));
        events.record(new BoardArmed(task.id(), armed.id(), armed.file(), armed.bound()), Actor.worker(worker), now);
        return armed;
    }

    private static void requireItsOwnWorker(Task task, WorkerId worker) {
        if (!worker.task().equals(task.id())) {
            throw new Refusal(worker + " arms boards for its own task, " + worker.task() + ", not for " + task.id() + ".");
        }
    }

    private Path boardFileOf(Task task, Path file) {
        Path taskDirectory = home.taskDirectory(task.id());
        if (!file.isAbsolute() || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(HTML) || !Files.isRegularFile(file)) {
            throw new Refusal("A research board is an HTML file that exists, named by its absolute path; " + file + " is not.");
        }
        Path real = realPathOf(file);
        if (!Files.isDirectory(taskDirectory) || !real.startsWith(realPathOf(taskDirectory))) {
            throw new Refusal("A research board lives in its task's directory, " + taskDirectory + "; " + file + " does not.");
        }
        return real;
    }

    private void requireOpen(DecisionNumber number) {
        if (!decisions.get(number).isOpen()) {
            throw new Refusal(number + " is already answered; a board answers only open decisions.");
        }
    }

    private BoardRound withItsCardRaised(BoardRound round, Task task, WorkerId worker, Optional<Board> listening) {
        return round.carriedCard().map(card -> round.withItsCardRaisedAs(raised(card, task, worker, listening))).orElse(round);
    }

    private DecisionNumber raised(BoardCard card, Task task, WorkerId worker, Optional<Board> listening) {
        decisions.ofTask(task.id()).ifPresent(earlier -> requireRaisedAgainOnlyFromItsOwnBoard(earlier, card, listening));
        return raiseDecision.raise(new TaskReference.ById(task.id()), card.card(), Actor.worker(worker));
    }

    private static void requireRaisedAgainOnlyFromItsOwnBoard(Decision earlier, BoardCard card, Optional<Board> listening) {
        if (!earlier.isOpen() && earlier.card().equals(card.card())) {
            throw new Refusal(earlier.number() + " on " + earlier.task() + " already holds the captain's answer to this card (\""
                    + earlier.answer().verbatim() + "\"), so a board round raises it no more; arm the round without --card.");
        }
        if (earlier.isOpen() && !earlier.card().equals(card.card())
                && listening.filter(board -> board.bindings().contains(card.raisedAs(earlier.number()))).isEmpty()) {
            throw new Refusal(earlier.number() + " already waits on the captain on " + earlier.task() + " (\"" + earlier.card().title()
                    + "\"); if the board answers it, arm the round with that very card under --card, or report the new question to EDI with "
                    + "edi status needs-decision.");
        }
    }

    private Board postedAgain(Board listening, Task task, BoardRound round) {
        if (!listening.task().equals(task.id())) {
            throw new Refusal(listening.file() + " is already armed for " + listening.task() + ".");
        }
        Board posted = listening.posted(round.carryingForward(openBindingsOf(listening)));
        boards.update(posted);
        return posted;
    }

    private List<BoardBinding> openBindingsOf(Board listening) {
        return listening.bindings().stream().filter(binding -> decisions.get(binding.decision()).isOpen()).toList();
    }

    private static Path realPathOf(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException unreadable) {
            throw new Refusal(path + " cannot be read: " + unreadable.getMessage());
        }
    }
}
