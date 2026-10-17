package io.github.ythibos.edi.application.ledger;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.ledger.FleetLedger;
import io.github.ythibos.edi.adapter.ledger.LedgerEntry;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftMerged;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftOpened;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class EmitFleetLedger {

    public static final String FOLLOWER = "fleet-ledger";

    private final MachineProfile machine;
    private final FleetLedger ledger;
    private final TaskStore tasks;
    private final TransactionTemplate transaction;
    private final Map<String, Function<LoggedEvent, Optional<LedgerEntry>>> entries = Map.of(
            TaskDispatched.class.getSimpleName(), this::firstDispatch,
            StatusReported.class.getSimpleName(), EmitFleetLedger::status,
            DraftRecorded.class.getSimpleName(), EmitFleetLedger::draftReady,
            DraftOpened.class.getSimpleName(), EmitFleetLedger::draftReady,
            DraftMerged.class.getSimpleName(), EmitFleetLedger::merged,
            TaskCleanedUp.class.getSimpleName(), EmitFleetLedger::cleanedUp);

    public EmitFleetLedger(MachineProfile machine, FleetLedger ledger, TaskStore tasks, TransactionTemplate transaction) {
        this.machine = machine;
        this.ledger = ledger;
        this.tasks = tasks;
        this.transaction = transaction;
    }

    public void follow(LoggedEvent event) {
        if (machine.time().fleetLedger()) {
            entries.getOrDefault(event.type(), _ -> Optional.empty()).apply(event).ifPresent(ledger::append);
        }
    }

    private Optional<LedgerEntry> firstDispatch(LoggedEvent event) {
        if (WorkerId.parse(event.text("worker")).incarnation() != 1) {
            return Optional.empty();
        }
        Intake intake = transaction.execute(_ -> tasks.get(event.task()).intake());
        return Optional.of(new LedgerEntry.Dispatched(event.task(), event.at(), intake.kind().word(), intake.projects().getFirst().value(),
                event.text("model"), intake.workKey() == null ? null : intake.workKey().toString()));
    }

    private static Optional<LedgerEntry> status(LoggedEvent event) {
        return Optional.of(new LedgerEntry.Status(event.task(), event.at(), event.text("state"), event.text("key"), event.text("text")));
    }

    private static Optional<LedgerEntry> draftReady(LoggedEvent event) {
        return Optional.of(new LedgerEntry.DraftReady(event.task(), event.at(), event.text("url")));
    }

    private static Optional<LedgerEntry> merged(LoggedEvent event) {
        return Optional.of(new LedgerEntry.Merged(event.task(), event.at(), event.text("url")));
    }

    private static Optional<LedgerEntry> cleanedUp(LoggedEvent event) {
        return Optional.of(new LedgerEntry.CleanedUp(event.task(), event.at()));
    }
}
