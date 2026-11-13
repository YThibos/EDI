package io.github.ythibos.edi.application.primary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.store.CaptainMessageStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.TurnItemStore;
import io.github.ythibos.edi.adapter.store.WakeStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.presence.ChangePresence;
import io.github.ythibos.edi.application.presence.PresenceQuery;
import io.github.ythibos.edi.application.presence.ReturnBrief;
import io.github.ythibos.edi.application.presence.ReturnBriefQuery;
import io.github.ythibos.edi.application.rule.FiredRules;
import io.github.ythibos.edi.application.wake.WakeStreams;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.domain.authority.AnsweredQuestion;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.PrimaryTurn;
import io.github.ythibos.edi.domain.primary.Prompt;
import io.github.ythibos.edi.domain.primary.SessionProcess;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.primary.SessionStart;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.primary.TurnCircumstances;
import io.github.ythibos.edi.domain.primary.TurnEnd;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleFired;
import io.github.ythibos.edi.domain.rule.RuleOutcome;
import io.github.ythibos.edi.domain.rule.RuleReason;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.LimitStop;

@Service
public class PrimaryHooks {

    private final PrimarySessionStore sessions;
    private final PrimarySessions roles;
    private final CaptainMessageStore captainMessages;
    private final ChangePresence presence;
    private final PresenceQuery presenceNow;
    private final ReturnBriefQuery returnBriefs;
    private final TaskStore tasks;
    private final TurnItemStore turnItems;
    private final EventLog events;
    private final ProcessTable processes;
    private final WakeStreams streams;
    private final WakeStore wakes;
    private final WorkerRuntime runtime;
    private final FiredRules firedRules;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PrimaryHooks(PrimarySessionStore sessions, PrimarySessions roles, CaptainMessageStore captainMessages, ChangePresence presence,
            PresenceQuery presenceNow, ReturnBriefQuery returnBriefs, TaskStore tasks, TurnItemStore turnItems, EventLog events, ProcessTable processes, WakeStreams streams,
            WakeStore wakes, WorkerRuntime runtime, FiredRules firedRules, Clock clock, TransactionTemplate transaction) {
        this.sessions = sessions;
        this.roles = roles;
        this.captainMessages = captainMessages;
        this.presence = presence;
        this.presenceNow = presenceNow;
        this.returnBriefs = returnBriefs;
        this.tasks = tasks;
        this.turnItems = turnItems;
        this.events = events;
        this.processes = processes;
        this.streams = streams;
        this.wakes = wakes;
        this.runtime = runtime;
        this.firedRules = firedRules;
        this.clock = clock;
        this.transaction = transaction;
    }

    public SessionStart sessionStarted(UUID id, Long claudePid, SessionSource source) {
        SessionProcess process = claudePid == null ? null : processes.identify(claudePid).orElse(null);
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Optional<PrimarySession> known = sessions.get(id);
            PrimarySession session = known.map(earlier -> earlier.restarted(process)).orElseGet(() -> PrimarySession.started(id, process, now));
            sessions.save(session);
            if (roles.role().isVacant()) {
                claim(session, now);
            }
            return new SessionStart(source.isALaunch(), known.isEmpty(), roles.role().standingOf(id));
        });
    }

    public Optional<ReturnBrief> promptSubmitted(UUID id, String text) {
        Optional<Presence> left = transaction.execute(_ -> {
            Instant now = clock.now();
            Prompt prompt = new Prompt(text);
            PrimarySession session = registered(id, now).promptSubmitted(prompt, events.latestSequence(), now);
            sessions.save(session);
            turnItems.clear(id);
            if (!prompt.isTypedByTheCaptain()) {
                return Optional.<Presence>empty();
            }
            captainMessages.recordTyped(id, prompt.text(), now);
            claim(session, now);
            return presence.captainTypedAt(now);
        });
        return left.map(returnBriefs::since);
    }

    public HookAnswer dialogAnswered(UUID id, List<AnsweredQuestion> answers) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            sessions.save(registered(id, now));
            answers.forEach(answer -> captainMessages.recordDialogAnswer(id, answer, now));
        });
        return HookAnswer.PROCEED;
    }

    public TurnEnd stopping(UUID id) {
        TurnEnd end = transaction.execute(_ -> sessions.get(id).map(this::answeredStop).orElse(TurnEnd.ENDS));
        if (end == TurnEnd.STARTS_THE_WAIT) {
            firedRules.record(new RuleFired(Rule.TURN_END_WITH_WORK_PENDING, RuleOutcome.REFUSED, Actor.PRIMARY, null, id,
                    RuleReason.of(end.instruction())));
        }
        return end;
    }

    public HookAnswer stopFailed(UUID id, String transcript) {
        Optional<PrimarySession> stopped = transaction.execute(_ -> sessions.get(id));
        stopped.flatMap(session -> runtime.usageLimitStopIn(transcript, session.startedAt()))
                .map(LimitStop::limit)
                .ifPresent(limit -> transaction.executeWithoutResult(_ -> sessions.get(id)
                        .ifPresent(session -> sessions.save(session.stoppedOn(limit, clock.now())))));
        return HookAnswer.PROCEED;
    }

    public HookAnswer notified(UUID id, String kind) {
        transaction.executeWithoutResult(_ -> sessions.get(id).ifPresent(session -> sessions.save(session.notified(kind, clock.now()))));
        return HookAnswer.PROCEED;
    }

    public HookAnswer sessionEnded(UUID id) {
        transaction.executeWithoutResult(_ -> sessions.get(id).ifPresent(this::ended));
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

    private TurnEnd answeredStop(PrimarySession session) {
        boolean supervises = roles.role().standingOf(session.id()) == Standing.PRIMARY;
        TurnEnd end = supervises ? endOf(session) : TurnEnd.ENDS;
        sessions.save(end.holdsTheStop() ? session.stopBlocked() : session.turnEnded());
        return end;
    }

    private TurnEnd endOf(PrimarySession session) {
        PrimaryTurn turn = session.turn();
        if (turn == null) {
            return TurnEnd.ENDS;
        }
        return TurnEnd.of(turn, new TurnCircumstances(events.anyAfter(turn.eventMark()), !turnItems.recordedIn(session.id()).recordsNothing(),
                presenceNow.current().keepsTheChatQuiet(), !tasks.inState(TaskState.IN_FLIGHT).isEmpty(), streams.liveOf(session.id()).isPresent()));
    }

    private void ended(PrimarySession session) {
        Instant now = clock.now();
        sessions.save(session.ended(now));
        turnItems.clear(session.id());
        wakes.acknowledgeDeliveredTo(session.id(), now);
    }
}
