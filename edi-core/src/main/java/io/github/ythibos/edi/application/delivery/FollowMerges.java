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
import io.github.ythibos.edi.application.task.TaskStages;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
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
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.StageTrigger.RequestMerged;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class FollowMerges {

    private static final Logger LOG = LoggerFactory.getLogger(FollowMerges.class);
    private static final String WEB_ADDRESS = "https://";

    private final TaskStore tasks;
    private final DeliveryStore deliveries;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final WorkerContext context;
    private final Forges forges;
    private final DeliveryRecords records;
    private final CloneRefreshes refreshes;
    private final CleanUpTask cleanUp;
    private final LandedWorkCheck landedWork;
    private final TaskStages stages;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public FollowMerges(TaskStore tasks, DeliveryStore deliveries, EventLog events, ProjectRegistry registry, WorkerContext context, Forges forges,
            DeliveryRecords records, CloneRefreshes refreshes, CleanUpTask cleanUp, LandedWorkCheck landedWork, TaskStages stages, Clock clock,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.deliveries = deliveries;
        this.events = events;
        this.registry = registry;
        this.context = context;
        this.forges = forges;
        this.records = records;
        this.refreshes = refreshes;
        this.cleanUp = cleanUp;
        this.landedWork = landedWork;
        this.stages = stages;
        this.clock = clock;
        this.transaction = transaction;
    }

    public MergedWork mergedSaysTheCaptain(String reference, Actor actor) {
        return transaction.execute(_ -> deliveryNamedBy(reference))
                .map(delivery -> deliveryFollowed(delivery, actor))
                .orElseGet(() -> requestFollowedFromTheForge(reference, actor));
    }

    public void followTheRequestOf(TaskId task) {
        try {
            followedAsTheForgeShowsIt(transaction.execute(_ -> context.of(task)));
        } catch (Refusal | ProgramFailure unfollowable) {
            LOG.warn("Could not look up the merge request of {}: {}", task, unfollowable.getMessage());
        }
    }

    public void followTheAcceptedRequest(TaskId task, MergeRequestView request) {
        transaction.execute(_ -> deliveries.of(task)).filter(delivery -> delivery.address().equals(request.address()))
                .ifPresentOrElse(recorded -> followedOnceAccepted(recorded, request), () -> followedOnceFound(task, request, Actor.CORE));
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
                ? deliveries.at(reference)
                : records.followedDeliveryOf(tasks.get(TaskReference.parse(reference)).id());
    }

    private MergedWork deliveryFollowed(Delivery delivery, Actor actor) {
        if (delivery.isMerged()) {
            return followedBefore(delivery, actor);
        }
        MergeRequestView view = viewOf(delivery);
        if (!view.isMerged()) {
            noticeAClosure(delivery, view, actor);
            throw notMerged(view);
        }
        return followed(delivery, view, actor);
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

    private MergeRequestView viewOf(Delivery delivery) {
        RegisteredForge forge = forgeOf(delivery);
        return forges.serving(forge).view(forge, delivery.address());
    }

    private RegisteredForge forgeOf(Delivery delivery) {
        ProjectEntry project = transaction.execute(_ -> registry.entryOf(tasks.get(delivery.task()).intake().projects().getFirst()));
        RegisteredForge forge = project.requireForge();
        if (!forge.repository().equals(delivery.address().repository())) {
            throw new Refusal(Rule.REQUEST_FOLLOWED, delivery.address() + " is not in " + forge.repository() + ", the repository " + project.project().name()
                    + " registers now, so it is no longer watched.");
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

    private static Refusal notMerged(MergeRequestView view) {
        return new Refusal(Rule.REQUEST_FOLLOWED, "The forge shows " + view.address() + " " + view.state().word() + ", not merged, so "
                + (view.state() == MergeRequestState.OPEN ? "it is still watched." : "the work is kept for the captain."));
    }

    private MergedWork followed(Delivery delivery, MergeRequestView view, Actor actor) {
        RequestMerged merge = followedMergeOf(delivery.task(), view);
        return cleanedUpOnceMerged(transaction.execute(_ -> recordedAsMerged(delivery, view, merge, actor)), actor);
    }

    private RequestMerged followedMergeOf(TaskId task, MergeRequestView view) {
        Workplace workplace = transaction.execute(_ -> context.of(task));
        return new RequestMerged(view.headCommit(), landedWork.commitsAfter(workplace, view.headCommit()));
    }

    private Delivery recordedAsMerged(Delivery delivery, MergeRequestView view, RequestMerged merge, Actor actor) {
        Instant now = clock.now();
        Delivery merged = delivery.observed(view, now);
        deliveries.save(merged);
        events.record(new DraftMerged(delivery.task(), view.address(), view.landedCommit()), actor, now);
        Task task = stages.moved(tasks.get(delivery.task()), merge, actor, now);
        refreshes.awaitTheMergeOf(task, view, now);
        return merged;
    }

    private MergedWork cleanedUpOnceMerged(Delivery merged, Actor actor) {
        refreshes.refreshEveryClone();
        CleanUp cleanUp = cleanUpWhatTheClonesHold(actor).getOrDefault(merged.task(), CleanUp.awaitingTheRefresh());
        return new MergedWork(merged.task(), merged, cleanUp);
    }

    private MergedWork requestFollowedFromTheForge(String reference, Actor actor) {
        return reference.startsWith(WEB_ADDRESS)
                ? requestFollowedAt(reference, actor)
                : requestFollowedFor(tasks.get(TaskReference.parse(reference)).id(), actor);
    }

    private MergedWork requestFollowedAt(String url, Actor actor) {
        FoundRequest found = requestAt(url).orElseThrow(() -> new Refusal(Rule.DELIVERY_RECORDED, "Nothing was delivered at " + url + "."));
        return requestFollowedOnTheCaptainsWord(found.task(), found.request(), actor);
    }

    private Optional<FoundRequest> requestAt(String url) {
        return transaction.execute(_ -> tasks.inState(TaskState.IN_FLIGHT)).stream()
                .flatMap(task -> requestOf(task.id(), url).stream())
                .findFirst();
    }

    private Optional<FoundRequest> requestOf(TaskId task, String url) {
        try {
            Workplace workplace = transaction.execute(_ -> context.of(task));
            return records.followsARequest(workplace) && workplace.project().forge().repository().holds(url)
                    ? records.requestFrom(workplace).filter(request -> request.address().url().equals(url))
                            .map(request -> new FoundRequest(task, request))
                    : Optional.empty();
        } catch (Refusal | ProgramFailure unfollowable) {
            LOG.warn("Could not look up the merge request of {}: {}", task, unfollowable.getMessage());
            return Optional.empty();
        }
    }

    private MergedWork requestFollowedFor(TaskId task, Actor actor) {
        Workplace workplace = transaction.execute(_ -> context.of(task));
        if (!records.followsARequest(workplace)) {
            return new MergedWork(task, null, cleanedUpAfter(new TaskReference.ById(task), actor).triedAgainOnTheCaptainsWord());
        }
        return requestFollowedOnTheCaptainsWord(task, records.requireTheRequestFrom(workplace), actor);
    }

    private MergedWork requestFollowedOnTheCaptainsWord(TaskId task, MergeRequestView request, Actor actor) {
        if (!request.isMerged()) {
            transaction.executeWithoutResult(_ -> records.recorded(task, request, actor));
            throw notMerged(request);
        }
        return recordedAndFollowed(task, request, actor);
    }

    private MergedWork recordedAndFollowed(TaskId task, MergeRequestView request, Actor actor) {
        RequestMerged merge = followedMergeOf(task, request);
        Delivery merged = transaction.execute(_ -> recordedAsMerged(records.recorded(task, request, actor), request, merge, actor));
        return cleanedUpOnceMerged(merged, actor);
    }

    private void followedAsTheForgeShowsIt(Workplace workplace) {
        TaskId task = workplace.task().id();
        if (transaction.execute(_ -> records.awaitsTheRequestOf(workplace))) {
            records.requestFrom(workplace).ifPresent(request -> followedOnceFound(task, request, Actor.CORE));
        } else {
            transaction.execute(_ -> records.followedDeliveryOf(task)).filter(Delivery::isOpen).ifPresent(this::observed);
        }
    }

    private void followedOnceFound(TaskId task, MergeRequestView request, Actor actor) {
        if (request.isMerged()) {
            recordedAndFollowed(task, request, actor);
        } else {
            transaction.executeWithoutResult(_ -> records.recorded(task, request, actor));
        }
    }

    private void followedOnceAccepted(Delivery recorded, MergeRequestView request) {
        if (recorded.isMerged()) {
            followedBefore(recorded, Actor.CORE);
        } else if (request.isMerged()) {
            followed(recorded, request, Actor.CORE);
        } else {
            keptAsTheForgeShowsIt(recorded, request);
        }
    }

    private void keptAsTheForgeShowsIt(Delivery recorded, MergeRequestView request) {
        transaction.executeWithoutResult(_ -> deliveries.save(recorded.observed(request, clock.now())));
    }

    private void observedOnItsOwn(Delivery delivery) {
        try {
            observed(delivery);
        } catch (Refusal | ProgramFailure unobservable) {
            LOG.warn("Could not watch the draft of {}: {}", delivery.task(), unobservable.getMessage());
        }
    }

    private void observed(Delivery delivery) {
        MergeRequestView view = viewOf(delivery);
        if (view.isMerged()) {
            followed(delivery, view, Actor.CORE);
        } else if (view.state() == MergeRequestState.CLOSED) {
            noticeAClosure(delivery, view, Actor.CORE);
        } else {
            keptAsTheForgeShowsIt(delivery, view);
        }
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

    private record FoundRequest(TaskId task, MergeRequestView request) {
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
            return new CleanUp(false, "the clone has not fetched the merge yet; the cleanup follows once it has", false);
        }

        boolean countsAsTheAttempt() {
            return !stopped;
        }

        CleanUp triedAgainInTheNextRound() {
            return continuedWith("the next round tries again");
        }

        CleanUp triedAgainOnTheCaptainsWord() {
            return continuedWith("run edi merged or edi cleanup again once the failure clears");
        }

        private CleanUp continuedWith(String nextStep) {
            return stopped ? new CleanUp(false, refusal + "; " + nextStep, true) : this;
        }
    }
}
