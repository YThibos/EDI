package io.github.ythibos.edi.application.authority;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.CaptainMessageStore;
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
import io.github.ythibos.edi.domain.authority.CaptainMessages;
import io.github.ythibos.edi.domain.authority.CaptainsAuthority;
import io.github.ythibos.edi.domain.authority.CaptainsAuthority.Heard;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.authority.CitedWords;
import io.github.ythibos.edi.domain.authority.Exchange;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.CaptainSaid;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class CaptainsWordEvidence {

    private final CaptainWordStore words;
    private final CaptainMessageStore messages;
    private final PrimarySessionStore sessions;
    private final PrimarySessions roles;
    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CaptainsWordEvidence(CaptainWordStore words, CaptainMessageStore messages, PrimarySessionStore sessions, PrimarySessions roles, TaskStore tasks,
            DecisionStore decisions, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.words = words;
        this.messages = messages;
        this.sessions = sessions;
        this.roles = roles;
        this.tasks = tasks;
        this.decisions = decisions;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Authorisation record(CallingSession calling, Citation citation, TaskReference task, CaptainWordAct act, Actor actor) {
        Exchange exchange = exchangeOf(calling);
        CitedWords cited = typedIn(exchange, citation);
        return transaction.execute(_ -> {
            TaskId concerned = task == null ? null : tasks.get(task).id();
            Authorisation word = new Authorisation(exchange.session(), concerned, act, cited.verbatim(), clock.now());
            words.save(word);
            if (concerned != null) {
                events.record(new CaptainSaid(concerned, word.verbatim()), actor, word.saidAt());
            }
            return word;
        });
    }

    public String requireCited(CallingSession calling, Citation citation) {
        return messagesOf(exchangeOf(calling)).cite(citation).verbatim();
    }

    public void requireTypedWithItsCommand(CallingSession calling, String command, String verbatim) {
        CaptainMessages sent = messagesOf(exchangeOf(calling));
        if (!sent.typed(command + " " + verbatim.strip()) && !sent.typed(verbatim)) {
            throw new Refusal(Rule.TYPED_WORDS, "The captain did not type \"" + verbatim.strip() + "\" in this session; give the words the captain typed after "
                    + command + ", exactly.");
        }
    }

    public CaptainMessages messages(CallingSession calling) {
        return messagesOf(exchangeOf(calling));
    }

    public CaptainsWord requireOnTask(CallingSession calling, TaskId task, CaptainWordAct act) {
        Exchange exchange = exchangeOf(calling);
        return transaction.execute(_ -> CaptainsAuthority.onTask(task, act, exchange, heardIn(exchange), decisions.everyOneOfTask(task)))
                .orElseThrow(() -> new Refusal(Rule.CAPTAINS_WORD, "To " + act.sentence() + ", you need the captain's word on " + task
                        + " in this session: record it with edi captain said --task " + task
                        + " --message <n>, have the captain choose a dialog option that opens with " + act.word() + " and names " + task
                        + " and no other task, or have the captain choose, here, in the console or on a research board, the option of a decision on it"
                        + " that authorises " + act.word() + "."));
    }

    public CaptainsWord requireSaidForActOnTask(CallingSession calling, TaskId task, CaptainWordAct act) {
        Exchange exchange = exchangeOf(calling);
        return transaction.execute(_ -> CaptainsAuthority.saidForActOnTask(task, act, exchange, heardIn(exchange)))
                .orElseThrow(() -> new Refusal(Rule.CAPTAINS_WORD, "To " + act.sentence() + ", you need the captain's word on " + task
                        + " in this session recorded for it: record it with edi captain said --task " + task + " --for " + act.word()
                        + " --message <n>, or have the captain choose a dialog option that opens with " + act.word() + " and names " + task + " and no other task."));
    }

    public CaptainsWord requireOnTheHome(CallingSession calling, CaptainWordAct act) {
        return onTheHome(exchangeOf(calling), act).orElseThrow(() -> new Refusal(Rule.CAPTAINS_WORD, "To " + act.sentence()
                + ", you need the captain's word in this session: record it with edi captain said --message <n>,"
                + " or have the captain choose a dialog option that opens with " + act.word() + "."));
    }

    public Optional<CaptainsWord> onTheHome(CallingSession calling, CaptainWordAct act) {
        return currentExchange(calling).flatMap(exchange -> onTheHome(exchange, act));
    }

    private Exchange exchangeOf(CallingSession calling) {
        return currentExchange(calling).orElseThrow(() -> new Refusal(Rule.TYPED_WORDS, calling.namesItself()
                ? "Session " + calling.id() + " is not a known primary session, so the captain's word cannot be heard there."
                : "No session holds the primary role, so the captain's word cannot be heard; the captain gives it in the primary session or the console."));
    }

    private Optional<Exchange> currentExchange(CallingSession calling) {
        return transaction.execute(_ -> calling.namesItself() ? sessions.get(calling.id()) : roles.holder())
                .map(session -> new Exchange(session.id(), session.startedAt()));
    }

    private CitedWords typedIn(Exchange exchange, Citation citation) {
        CitedWords cited = messagesOf(exchange).cite(citation);
        if (cited.comeFromADialog()) {
            throw new Refusal(Rule.TYPED_WORDS, "A dialog answer is the captain's word by itself, for the act its chosen option opens with and the task it names,"
                    + " so it is not recorded again; cite a message the captain typed.");
        }
        return cited;
    }

    private CaptainMessages messagesOf(Exchange exchange) {
        return transaction.execute(_ -> messages.sentIn(exchange.session()));
    }

    private Heard heardIn(Exchange exchange) {
        return new Heard(words.heardIn(exchange.session()), messages.sentIn(exchange.session()).dialogAnswers(), Set.copyOf(tasks.ids()));
    }

    private Optional<CaptainsWord> onTheHome(Exchange exchange, CaptainWordAct act) {
        return transaction.execute(_ -> CaptainsAuthority.onTheHome(act, exchange, heardIn(exchange)));
    }
}
