package io.github.ythibos.edi.application.primary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.domain.primary.BackgroundJob;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.PrimaryTurn;
import io.github.ythibos.edi.domain.primary.Prompt;
import io.github.ythibos.edi.domain.primary.SessionProcess;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.primary.TurnEnd;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class PrimaryHooks {

    private final PrimarySessionStore sessions;
    private final PrimarySessions roles;
    private final TaskStore tasks;
    private final EventLog events;
    private final ProcessTable processes;
    private final WorkerRuntime runtime;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PrimaryHooks(PrimarySessionStore sessions, PrimarySessions roles, TaskStore tasks, EventLog events, ProcessTable processes,
            WorkerRuntime runtime, Clock clock, TransactionTemplate transaction) {
        this.sessions = sessions;
        this.roles = roles;
        this.tasks = tasks;
        this.events = events;
        this.processes = processes;
        this.runtime = runtime;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void sessionStarted(UUID id, Long claudePid) {
        SessionProcess process = claudePid == null ? null : processes.identify(claudePid).orElse(null);
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            PrimarySession session = sessions.get(id).map(known -> known.restarted(process)).orElseGet(() -> PrimarySession.started(id, process, now));
            sessions.save(session);
            if (roles.role().isVacant()) {
                claim(session, now);
            }
        });
    }

    public HookAnswer promptSubmitted(UUID id, String text) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            Prompt prompt = new Prompt(text);
            PrimarySession session = registered(id, now).promptSubmitted(prompt, events.latestSequence(), now);
            sessions.save(session);
            if (prompt.isTypedByTheCaptain()) {
                sessions.recordTypedPrompt(id, prompt, now);
                claim(session, now);
            }
        });
        return HookAnswer.PROCEED;
    }

    public HookAnswer stopping(UUID id, List<BackgroundJob> backgroundJobs) {
        return transaction.execute(_ -> sessions.get(id).map(session -> answeredStop(session, backgroundJobs)).orElse(HookAnswer.PROCEED));
    }

    public HookAnswer stopFailed(UUID id, String transcript) {
        Optional<PrimarySession> stopped = transaction.execute(_ -> sessions.get(id));
        stopped.flatMap(session -> runtime.usageLimitStopIn(transcript, session.startedAt()))
                .ifPresent(limit -> transaction.executeWithoutResult(_ -> sessions.get(id)
                        .ifPresent(session -> sessions.save(session.stoppedOn(limit, clock.now())))));
        return HookAnswer.PROCEED;
    }

    public HookAnswer notified(UUID id, String kind) {
        transaction.executeWithoutResult(_ -> sessions.get(id).ifPresent(session -> sessions.save(session.notified(kind, clock.now()))));
        return HookAnswer.PROCEED;
    }

    public HookAnswer sessionEnded(UUID id) {
        transaction.executeWithoutResult(_ -> sessions.get(id).ifPresent(session -> sessions.save(session.ended(clock.now()))));
        return HookAnswer.PROCEED;
    }

    public void reportRendered(UUID id) {
        transaction.executeWithoutResult(_ -> sessions.get(id).ifPresent(session -> sessions.save(session.reported(clock.now()))));
    }

    private void claim(PrimarySession claimant, Instant now) {
        sessions.running().stream()
                .filter(other -> !other.id().equals(claimant.id()) && other.hasClaimedTheRole())
                .forEach(other -> sessions.save(other.relinquished()));
        sessions.save(claimant.claimed(now));
    }

    private PrimarySession registered(UUID id, Instant now) {
        return sessions.get(id).orElseGet(() -> PrimarySession.started(id, null, now));
    }

    private HookAnswer answeredStop(PrimarySession session, List<BackgroundJob> backgroundJobs) {
        boolean supervises = roles.role().standingOf(session.id()) == Standing.PRIMARY;
        Optional<String> blocked = supervises ? blockingReason(session.turn(), backgroundJobs) : Optional.empty();
        if (blocked.isPresent()) {
            sessions.save(session.stopBlocked());
            return HookAnswer.blockTheStop(blocked.get());
        }
        sessions.save(session.turnEnded());
        return HookAnswer.PROCEED;
    }

    private Optional<String> blockingReason(PrimaryTurn turn, List<BackgroundJob> backgroundJobs) {
        if (turn == null) {
            return Optional.empty();
        }
        boolean stateChanged = events.anyAfter(turn.eventMark());
        boolean workInFlight = !tasks.inState(TaskState.IN_FLIGHT).isEmpty();
        return TurnEnd.blockingReason(turn, stateChanged, workInFlight, BackgroundJob.anyWaitsForEdi(backgroundJobs));
    }
}
