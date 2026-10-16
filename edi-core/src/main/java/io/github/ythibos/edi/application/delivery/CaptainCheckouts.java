package io.github.ythibos.edi.application.delivery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.delivery.ChangedPath;
import io.github.ythibos.edi.domain.delivery.LocalChange;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveredToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeAppliedToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeNotApplied;

@Component
public class CaptainCheckouts {

    private static final String PATCH_FILE = "change.patch";

    private final Git git;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CaptainCheckouts(Git git, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.git = git;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void deliverTheBranch(Workplace workplace) {
        record(branchDelivered(workplace));
    }

    public void applyTheChange(Workplace workplace) {
        record(changeApplied(workplace));
    }

    private DomainEvent branchDelivered(Workplace workplace) {
        Path checkout = workplace.project().captainCheckout();
        if (checkout == null) {
            return branchNotDelivered(workplace, workplace.project().project().name() + " names no captainCheckout in the project registry");
        }
        try {
            git.fetchBranchInto(checkout, workplace.worktree(), workplace.dispatch().branch());
            return branchArrivedIn(checkout, workplace);
        } catch (ProgramFailure refused) {
            return branchNotDelivered(workplace, refused.getMessage());
        }
    }

    private DomainEvent branchArrivedIn(Path checkout, Workplace workplace) {
        String head = git.head(workplace.worktree());
        Optional<String> delivered = git.commitAt(checkout, "refs/heads/" + workplace.dispatch().branch());
        if (delivered.filter(head::equals).isEmpty()) {
            return branchNotDelivered(workplace, "the captain's checkout holds " + delivered.orElse("no " + workplace.dispatch().branch())
                    + " after the fetch, not the copy's head " + head);
        }
        return new BranchDeliveredToCaptain(workplace.task().id(), workplace.dispatch().branch());
    }

    private static DomainEvent branchNotDelivered(Workplace workplace, String because) {
        return new BranchDeliveryFailed(workplace.task().id(), workplace.dispatch().branch(), because);
    }

    private DomainEvent changeApplied(Workplace workplace) {
        Path checkout = workplace.project().captainCheckout();
        if (checkout == null) {
            return changeNotApplied(workplace, workplace.project().project().name() + " names no captainCheckout in the project registry");
        }
        try {
            git.fetchOrigin(workplace.project().projectClone());
            LocalChange change = git.changeSince(workplace.worktree(), workplace.baseRef());
            Path patch = written(workplace.taskDirectory().resolve(PATCH_FILE), change.patch());
            return reasonNotToApply(change, checkout, patch)
                    .map(because -> changeNotApplied(workplace, because))
                    .orElseGet(() -> appliedTo(checkout, change, workplace));
        } catch (ProgramFailure refused) {
            return changeNotApplied(workplace, refused.getMessage());
        }
    }

    private Optional<String> reasonNotToApply(LocalChange change, Path checkout, Path patch) {
        if (change.isEmpty()) {
            return Optional.of("the branch changes nothing beyond its base");
        }
        List<ChangedPath> held = change.heldForTheCaptain();
        if (!held.isEmpty()) {
            return Optional.of("the change " + String.join(", ", held.stream().map(ChangedPath::toString).toList())
                    + ", which waits for the captain's own look; the patch is " + patch);
        }
        List<String> uncommitted = git.uncommittedAmong(checkout, change.files());
        if (!uncommitted.isEmpty()) {
            return Optional.of("the captain's checkout has uncommitted work in " + String.join(", ", uncommitted) + "; the patch is " + patch);
        }
        return Optional.empty();
    }

    private DomainEvent appliedTo(Path checkout, LocalChange change, Workplace workplace) {
        git.applyToTheWorkingTree(checkout, change.patch());
        return new ChangeAppliedToCaptain(workplace.task().id(), change.files());
    }

    private static DomainEvent changeNotApplied(Workplace workplace, String because) {
        return new ChangeNotApplied(workplace.task().id(), because);
    }

    private void record(DomainEvent delivery) {
        transaction.executeWithoutResult(_ -> events.record(delivery, Actor.CORE, clock.now()));
    }

    private static Path written(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            return Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
