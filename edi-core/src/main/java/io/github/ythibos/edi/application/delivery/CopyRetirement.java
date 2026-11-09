package io.github.ythibos.edi.application.delivery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.board.BoardListeners;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardRetired;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;

@Component
public class CopyRetirement {

    private static final String BRANCHES = "refs/heads/";

    private final EdiHome home;
    private final WorkerStore workers;
    private final BoardStore boards;
    private final EventLog events;
    private final Git git;
    private final WorkerRuntime runtime;
    private final BoardListeners boardListeners;
    private final TransactionTemplate transaction;

    public CopyRetirement(EdiHome home, WorkerStore workers, BoardStore boards, EventLog events, Git git, WorkerRuntime runtime,
            BoardListeners boardListeners, TransactionTemplate transaction) {
        this.home = home;
        this.workers = workers;
        this.boards = boards;
        this.events = events;
        this.git = git;
        this.runtime = runtime;
        this.boardListeners = boardListeners;
        this.transaction = transaction;
    }

    public void requireNoWorkerAtWork(Workplace workplace, String remedy) {
        transaction.execute(_ -> workers.latestOf(workplace.task().id()))
                .filter(worker -> !worker.isRetired() && !worker.handedItsWorkOver() && runtime.isRunning(worker.window()))
                .ifPresent(working -> {
                    throw new Refusal(Rule.NO_WORKER_AT_WORK, working.id() + " still works on " + workplace.task().id() + "; " + remedy);
                });
    }

    public void removeNestedRepositories(Workplace workplace) {
        if (Files.isDirectory(workplace.worktree())) {
            git.nestedRepositories(workplace.worktree()).forEach(nested -> deleted(workplace.worktree().resolve(nested)));
        }
    }

    public void retireLanded(Workplace workplace) {
        retire(workplace, false);
    }

    public void retireDiscarding(Workplace workplace) {
        retire(workplace, true);
    }

    public void retireWorkers(TaskId task, String because, Instant now, Actor actor) {
        workers.allOf(task).stream().filter(worker -> !worker.isRetired()).forEach(worker -> retired(worker, because, now, actor));
    }

    public void retireBoards(TaskId task, Instant now, Actor actor) {
        boards.listeningOf(task).forEach(board -> retired(board, now, actor));
    }

    public void stopListeningFor(TaskId task) {
        boardListeners.forget(task);
    }

    private void retire(Workplace workplace, boolean discardingWork) {
        List<Worker> incarnations = transaction.execute(_ -> workers.allOf(workplace.task().id()));
        incarnations.stream().filter(worker -> !worker.isRetired()).forEach(worker -> runtime.close(worker.window()));
        Path clone = workplace.project().projectClone();
        if (Files.isDirectory(workplace.worktree())) {
            removeTheCopy(clone, workplace.worktree(), discardingWork);
        }
        if (git.commitAt(clone, BRANCHES + workplace.dispatch().branch()).isPresent()) {
            git.deleteBranch(clone, workplace.dispatch().branch());
        }
        incarnations.forEach(worker -> deleted(home.workerState(worker.id())));
    }

    private void retired(Worker worker, String because, Instant now, Actor actor) {
        workers.update(worker.retired(now));
        events.record(new WorkerRetired(worker.id().task(), worker.id(), because), actor, now);
    }

    private void retired(Board board, Instant now, Actor actor) {
        boards.update(board.retired(now));
        events.record(new BoardRetired(board.task(), board.id()), actor, now);
    }

    private void removeTheCopy(Path clone, Path copy, boolean discardingWork) {
        if (discardingWork) {
            git.discardWorktree(clone, copy);
        } else {
            git.removeWorktree(clone, copy);
        }
    }

    private static void deleted(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException undeletable) {
            throw new UncheckedIOException(undeletable);
        }
    }
}
