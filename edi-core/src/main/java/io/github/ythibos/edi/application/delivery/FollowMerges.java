package io.github.ythibos.edi.application.delivery;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.CloneRefresh;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftClosed;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftMerged;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class FollowMerges {

    private static final Logger LOG = LoggerFactory.getLogger(FollowMerges.class);
    private static final String WEB_ADDRESS = "https://";

    private final TaskStore tasks;
    private final DeliveryStore deliveries;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final Forges forges;
    private final CloneRefreshes refreshes;
    private final CleanUpTask cleanUp;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public FollowMerges(TaskStore tasks, DeliveryStore deliveries, EventLog events, ProjectRegistry registry, Forges forges, CloneRefreshes refreshes,
            CleanUpTask cleanUp, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.deliveries = deliveries;
        this.events = events;
        this.registry = registry;
        this.forges = forges;
        this.refreshes = refreshes;
        this.cleanUp = cleanUp;
        this.clock = clock;
        this.transaction = transaction;
    }

    public MergedWork mergedSaysTheCaptain(String reference, Actor actor) {
        Optional<Delivery> delivery = transaction.execute(_ -> deliveryNamedBy(reference));
        if (delivery.isEmpty()) {
            return new MergedWork(tasks.get(TaskReference.parse(reference)).id(), null,
                    cleanedUpAfter(TaskReference.parse(reference), actor).triedAgainOnTheCaptainsWord());
        }
        if (delivery.get().state() == MergeRequestState.MERGED) {
            return followedBefore(delivery.get(), actor);
        }
        MergeRequestView view = viewOf(delivery.get());
        if (!view.isMerged()) {
            noticeAClosure(delivery.get(), view, actor);
            throw new Refusal("The forge shows " + view.address() + " " + view.state().word() + ", not merged, so EDI keeps "
                    + (view.state() == MergeRequestState.OPEN ? "watching it." : "the work and waits for the captain."));
        }
        return followed(delivery.get(), view, actor);
    }

    public void watchTheOpenDrafts() {
        transaction.execute(_ -> deliveries.inState(MergeRequestState.OPEN)).stream()
                .filter(this::isInFlight)
                .forEach(this::observedOnItsOwn);
    }

    public void finishEarlierMerges() {
        refreshes.refreshEveryClone();
        cleanUpWhatTheClonesHold(Actor.CORE);
    }

    private Optional<Delivery> deliveryNamedBy(String reference) {
        return reference.startsWith(WEB_ADDRESS)
                ? Optional.of(deliveries.at(reference).orElseThrow(() -> new Refusal("EDI delivered nothing at " + reference + ".")))
                : deliveries.of(tasks.get(TaskReference.parse(reference)).id());
    }

    private MergedWork followedBefore(Delivery delivery, Actor actor) {
        refreshes.refreshEveryClone();
        return new MergedWork(delivery.task(), delivery, cleanedUpOnceMore(delivery, actor));
    }

    private CleanUp cleanedUpOnceMore(Delivery delivery, Actor actor) {
        if (!isInFlight(delivery)) {
            return CleanUp.finished();
        }
        return refreshes.after(delivery.task()).filter(refresh -> !refresh.isPending())
                .map(refresh -> cleanedUpOnce(refresh, actor))
                .orElseGet(CleanUp::awaitingTheRefresh);
    }

    private boolean isInFlight(Delivery delivery) {
        return transaction.execute(_ -> !tasks.get(delivery.task()).isClosed());
    }

    private void observedOnItsOwn(Delivery delivery) {
        try {
            observed(delivery);
        } catch (Refusal | ProgramFailure unobservable) {
            LOG.warn("EDI could not watch the draft of {}: {}", delivery.task(), unobservable.getMessage());
        }
    }

    private void observed(Delivery delivery) {
        MergeRequestView view = viewOf(delivery);
        if (view.isMerged()) {
            followed(delivery, view, Actor.CORE);
        } else {
            noticeAClosure(delivery, view, Actor.CORE);
        }
    }

    private MergeRequestView viewOf(Delivery delivery) {
        RegisteredForge forge = forgeOf(delivery);
        return forges.serving(forge).view(forge, delivery.address());
    }

    private RegisteredForge forgeOf(Delivery delivery) {
        ProjectEntry project = transaction.execute(_ -> registry.entryOf(tasks.get(delivery.task()).intake().projects().getFirst()));
        RegisteredForge forge = project.requireForge();
        if (!forge.repository().equals(delivery.address().repository())) {
            throw new Refusal(delivery.address() + " is not in " + forge.repository() + ", the repository " + project.project().name()
                    + " registers now, so EDI no longer watches it.");
        }
        return forge;
    }

    private void noticeAClosure(Delivery delivery, MergeRequestView view, Actor actor) {
        if (view.state() != MergeRequestState.CLOSED || !delivery.changesWith(view)) {
            return;
        }
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            deliveries.save(delivery.observed(view, now));
            events.record(new DraftClosed(delivery.task(), delivery.address()), actor, now);
        });
    }

    private MergedWork followed(Delivery delivery, MergeRequestView view, Actor actor) {
        Delivery merged = recordedAsMerged(delivery, view, actor);
        refreshes.refreshEveryClone();
        CleanUp cleanUp = cleanUpWhatTheClonesHold(actor).getOrDefault(delivery.task(), CleanUp.awaitingTheRefresh());
        return new MergedWork(delivery.task(), merged, cleanUp);
    }

    private Delivery recordedAsMerged(Delivery delivery, MergeRequestView view, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Task task = tasks.get(delivery.task());
            Delivery merged = delivery.observed(view, now);
            deliveries.save(merged);
            events.record(new DraftMerged(task.id(), view.address(), view.landedCommit()), actor, now);
            refreshes.awaitTheMergeOf(task, view, now);
            return merged;
        });
    }

    private Map<TaskId, CleanUp> cleanUpWhatTheClonesHold(Actor actor) {
        return refreshes.awaitingCleanUp().stream().collect(Collectors.toMap(CloneRefresh::task, refresh -> cleanedUpOnce(refresh, actor)));
    }

    private CleanUp cleanedUpOnce(CloneRefresh refresh, Actor actor) {
        CleanUp outcome = cleanedUpAfter(new TaskReference.ById(refresh.task()), actor);
        if (outcome.countsAsTheAttempt()) {
            refreshes.cleanUpTried(refresh);
        }
        return outcome.triedAgainInTheNextRound();
    }

    private CleanUp cleanedUpAfter(TaskReference task, Actor actor) {
        try {
            cleanUp.cleanUp(task, actor);
            return CleanUp.finished();
        } catch (Refusal refused) {
            return CleanUp.refusedBecause(refused.getMessage());
        } catch (ProgramFailure | UncheckedIOException failed) {
            LOG.warn("The clean-up of {} stopped: {}", task, failed.getMessage());
            return CleanUp.stoppedBy(failed);
        }
    }

    public record MergedWork(TaskId task, Delivery delivery, CleanUp cleanUp) {
    }

    public record CleanUp(boolean done, String refusal, boolean stopped) {

        static CleanUp finished() {
            return new CleanUp(true, null, false);
        }

        static CleanUp refusedBecause(String because) {
            return new CleanUp(false, because, false);
        }

        static CleanUp stoppedBy(RuntimeException failure) {
            return new CleanUp(false, "the clean-up stopped: " + failure.getMessage(), true);
        }

        static CleanUp awaitingTheRefresh() {
            return new CleanUp(false, "the clone has not fetched the merge yet; EDI cleans up once it has", false);
        }

        boolean countsAsTheAttempt() {
            return !stopped;
        }

        CleanUp triedAgainInTheNextRound() {
            return continuedWith("EDI tries again in the next round");
        }

        CleanUp triedAgainOnTheCaptainsWord() {
            return continuedWith("run edi merged or edi cleanup again once the failure clears");
        }

        private CleanUp continuedWith(String nextStep) {
            return stopped ? new CleanUp(false, refusal + "; " + nextStep, true) : this;
        }
    }
}
