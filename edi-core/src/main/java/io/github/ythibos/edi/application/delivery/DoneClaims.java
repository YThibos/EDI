package io.github.ythibos.edi.application.delivery;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.publish.PublishCheck;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.delivery.SnapshotVersions;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DeliveryRefused;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.publish.AddedLine;
import io.github.ythibos.edi.domain.publish.TextVerdict;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class DoneClaims {

    private final WorkerContext context;
    private final Git git;
    private final InboxStore inbox;
    private final DeliveryRecords records;
    private final LandedWorkCheck landedWork;
    private final Forges forges;
    private final PublishCheck publishCheck;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DoneClaims(WorkerContext context, Git git, InboxStore inbox, DeliveryRecords records, LandedWorkCheck landedWork, Forges forges,
            PublishCheck publishCheck, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.context = context;
        this.git = git;
        this.inbox = inbox;
        this.records = records;
        this.landedWork = landedWork;
        this.forges = forges;
        this.publishCheck = publishCheck;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Optional<MergeRequestView> verify(WorkerId worker) {
        requireEveryMessageAcknowledged(worker);
        Workplace workplace = transaction.execute(_ -> context.of(worker.task()));
        return switch (workplace.kind()) {
            case SHIP -> verifyTheShip(worker, workplace);
            case SCOUT -> verifyTheScout(worker, workplace);
            case CAPTAIN -> Optional.empty();
        };
    }

    private void requireEveryMessageAcknowledged(WorkerId worker) {
        List<Integer> waiting = numbersWaitingIn(worker);
        if (!waiting.isEmpty()) {
            refuseAnyOf(worker, List.of(unacknowledgedGapOf(waiting)));
        }
    }

    private List<Integer> numbersWaitingIn(WorkerId worker) {
        return transaction.execute(_ -> inbox.unacknowledgedOf(worker.task())).stream().map(InboxMessage::number).toList();
    }

    private static String unacknowledgedGapOf(List<Integer> waiting) {
        if (waiting.size() == 1) {
            return "message " + waiting.getFirst() + " waits unacknowledged, so read it with edi inbox, act on it"
                    + " and acknowledge it with edi inbox ack " + waiting.getFirst();
        }
        return "messages " + numbersOf(waiting) + " wait unacknowledged, so read them with edi inbox, act on each"
                + " and acknowledge each with edi inbox ack <n>";
    }

    private static String numbersOf(List<Integer> waiting) {
        List<String> numbers = waiting.stream().map(String::valueOf).toList();
        return String.join(", ", numbers.subList(0, numbers.size() - 1)) + " and " + numbers.getLast();
    }

    private void refuseAnyOf(WorkerId worker, List<String> gaps) {
        if (gaps.isEmpty()) {
            return;
        }
        transaction.executeWithoutResult(_ -> events.record(new DeliveryRefused(worker.task(), worker, gaps), Actor.CORE, clock.now()));
        throw new Refusal("This done is accepted once its claims hold: " + String.join("; ", gaps) + ".");
    }

    private Optional<MergeRequestView> verifyTheShip(WorkerId worker, Workplace workplace) {
        requireTheCopy(workplace);
        Optional<MergeRequestView> request = requestOf(workplace);
        List<String> gaps = new ArrayList<>(snapshotGapsOf(workplace));
        gaps.addAll(deliveryGapsOf(workplace, request));
        refuseAnyOf(worker, gaps);
        return request;
    }

    private static void requireTheCopy(Workplace workplace) {
        if (!Files.isDirectory(workplace.worktree())) {
            throw new Refusal("The isolated copy " + workplace.worktree() + " is gone, so what the done claims cannot be checked.");
        }
    }

    private Optional<MergeRequestView> requestOf(Workplace workplace) {
        return records.followsARequest(workplace) ? records.requestFrom(workplace) : Optional.empty();
    }

    private List<String> snapshotGapsOf(Workplace workplace) {
        List<AddedLine> snapshots = SnapshotVersions.addedIn(git.buildFileLinesAddedSince(workplace.worktree(), workplace.baseRef()));
        return snapshots.isEmpty() ? List.of() : List.of(SnapshotVersions.refusalOf(snapshots));
    }

    private List<String> deliveryGapsOf(Workplace workplace, Optional<MergeRequestView> request) {
        return switch (workplace.delivery()) {
            case DRAFT_REVIEW -> requestGapsOf(workplace, request);
            case LOCAL_BRANCH, LOCAL_APPLY -> List.of();
            case REPORT -> reportGapsOf(workplace);
        };
    }

    private List<String> requestGapsOf(Workplace workplace, Optional<MergeRequestView> request) {
        if (workplace.project().forge() == null) {
            return List.of(workplace.project().project().name() + " registers no forge, so no merge request can carry this work");
        }
        return request.map(found -> gapsOfTheRequest(workplace, found))
                .orElseGet(() -> List.of(records.noRequestFrom(workplace)
                        + "; the pipeline opens it as a draft, and edi deliver opens it when the pipeline cannot"));
    }

    private List<String> gapsOfTheRequest(Workplace workplace, MergeRequestView request) {
        List<String> gaps = new ArrayList<>(request.gapsAsTheDeliveryOf(workplace.dispatch().branch(), workplace.base(), git.head(workplace.worktree())));
        textGapOf(workplace, request).ifPresent(gaps::add);
        return gaps;
    }

    private Optional<String> textGapOf(Workplace workplace, MergeRequestView request) {
        RegisteredForge forge = workplace.project().requireForge();
        TextVerdict verdict = publishCheck.checkText(workplace.worktree(), forges.serving(forge).textOf(forge, request.address()));
        return verdict.allowsPosting() ? Optional.empty() : Optional.of(verdict.asAGapOf(request.address().toString()));
    }

    private List<String> reportGapsOf(Workplace workplace) {
        List<String> gaps = new ArrayList<>(missingReportGapsOf(workplace, "it is all a report delivery delivers"));
        List<String> changes = landedWork.of(workplace).findings();
        if (!changes.isEmpty()) {
            gaps.add("a report delivery carries no change, yet " + String.join(" and ", changes));
        }
        return gaps;
    }

    private static List<String> missingReportGapsOf(Workplace workplace, String whyItIsNeeded) {
        return Files.isRegularFile(workplace.report())
                ? List.of()
                : List.of("the report " + workplace.report() + " does not exist, and " + whyItIsNeeded);
    }

    private Optional<MergeRequestView> verifyTheScout(WorkerId worker, Workplace workplace) {
        refuseAnyOf(worker, missingReportGapsOf(workplace, "a scout delivers its findings there"));
        return Optional.empty();
    }
}
