package io.github.ythibos.edi.application.authority;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.CaptainWordStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.Authorisation;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.authority.CaptainsAuthority;
import io.github.ythibos.edi.domain.authority.Exchange;
import io.github.ythibos.edi.domain.authority.Passage;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.CaptainSaid;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class CaptainsWordEvidence {

    private final CaptainWordStore words;
    private final PrimarySessionStore sessions;
    private final PrimarySessions roles;
    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CaptainsWordEvidence(CaptainWordStore words, PrimarySessionStore sessions, PrimarySessions roles, TaskStore tasks,
            DecisionStore decisions, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.words = words;
        this.sessions = sessions;
        this.roles = roles;
        this.tasks = tasks;
        this.decisions = decisions;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Authorisation record(CallingSession calling, String verbatim, TaskReference task, Actor actor) {
        Exchange exchange = exchangeOf(calling);
        requireTyped(exchange, verbatim);
        return transaction.execute(_ -> {
            TaskId concerned = task == null ? null : tasks.get(task).id();
            Authorisation word = new Authorisation(exchange.session(), concerned, verbatim.strip(), clock.now());
            words.save(word);
            if (concerned != null) {
                events.record(new CaptainSaid(concerned, word.verbatim()), actor, word.saidAt());
            }
            return word;
        });
    }

    public void requireTyped(CallingSession calling, String verbatim) {
        requireTyped(exchangeOf(calling), verbatim);
    }

    public void requireTypedWithItsCommand(CallingSession calling, String command, String verbatim) {
        Exchange exchange = exchangeOf(calling);
        if (!wasTyped(exchange, command + " " + verbatim.strip())) {
            requireTyped(exchange, verbatim);
        }
    }

    public CaptainsWord requireOnTask(CallingSession calling, TaskId task, CaptainWordAct act) {
        Exchange exchange = exchangeOf(calling);
        return transaction.execute(_ -> CaptainsAuthority.onTask(task, exchange, words.heardIn(exchange.session()), answersOn(task)))
                .orElseThrow(() -> new Refusal("To " + act.sentence() + ", EDI needs the captain's word on " + task
                        + " in this session: record it with edi captain said --task " + task
                        + " --text \"<his words>\", or have him answer a decision on it, here or in the console."));
    }

    public CaptainsWord requireOnTheHome(CallingSession calling, CaptainWordAct act) {
        return onTheHome(exchangeOf(calling)).orElseThrow(() -> new Refusal("To " + act.sentence()
                + ", EDI needs the captain's word in this session: record it with edi captain said --text \"<his words>\"."));
    }

    public Optional<CaptainsWord> onTheHome(CallingSession calling) {
        return currentExchange(calling).flatMap(this::onTheHome);
    }

    public Optional<Exchange> currentExchange(CallingSession calling) {
        return transaction.execute(_ -> calling.namesItself() ? sessions.get(calling.id()) : roles.holder())
                .map(session -> new Exchange(session.id(), session.startedAt()));
    }

    private Exchange exchangeOf(CallingSession calling) {
        return currentExchange(calling).orElseThrow(() -> new Refusal(calling.namesItself()
                ? "Session " + calling.id() + " is not a primary session EDI knows, so it cannot hear the captain's word there."
                : "No session holds the primary role, so EDI cannot hear the captain's word; he gives it in the primary session or the console."));
    }

    private Optional<CaptainsWord> onTheHome(Exchange exchange) {
        return transaction.execute(_ -> CaptainsAuthority.onTheHome(exchange, words.heardIn(exchange.session())));
    }

    private void requireTyped(Exchange exchange, String verbatim) {
        if (!wasTyped(exchange, verbatim)) {
            throw new Refusal("The captain did not type \"" + verbatim.strip() + "\" in this session; record his words exactly as he typed"
                    + " them, his whole message or one whole line of it.");
        }
    }

    private boolean wasTyped(Exchange exchange, String verbatim) {
        return !verbatim.isBlank() && transaction.execute(_ -> words.wasTyped(exchange.session(), Passage.of(verbatim)));
    }

    private List<CaptainAnswer> answersOn(TaskId task) {
        return decisions.ofTask(task).map(Decision::answer).stream().toList();
    }
}
