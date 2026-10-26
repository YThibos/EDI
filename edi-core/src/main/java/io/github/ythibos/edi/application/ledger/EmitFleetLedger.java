package io.github.ythibos.edi.application.ledger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.ledger.FleetLedger;
import io.github.ythibos.edi.adapter.ledger.LedgerEntry;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.task.WorkedTimes;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftMerged;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftOpened;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.WorkedTime;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class EmitFleetLedger {

    public static final String FOLLOWER = "fleet-ledger";

    private final MachineProfile machine;
    private final FleetLedger ledger;
    private final TaskStore tasks;
    private final WorkedTimes workedTimes;
    private final TransactionTemplate transaction;
    private final Map<String, Function<LoggedEvent, List<LedgerEntry>>> entries = Map.of(
            TaskDispatched.class.getSimpleName(), this::firstDispatch,
            StatusReported.class.getSimpleName(), EmitFleetLedger::status,
            DraftRecorded.class.getSimpleName(), EmitFleetLedger::draftReady,
            DraftOpened.class.getSimpleName(), EmitFleetLedger::draftReady,
            DraftMerged.class.getSimpleName(), EmitFleetLedger::merged,
            TaskCleanedUp.class.getSimpleName(), this::workedAndCleanedUp);

    public EmitFleetLedger(MachineProfile machine, FleetLedger ledger, TaskStore tasks, WorkedTimes workedTimes, TransactionTemplate transaction) {
        this.machine = machine;
        this.ledger = ledger;
        this.tasks = tasks;
        this.workedTimes = workedTimes;
        this.transaction = transaction;
    }

    public void follow(LoggedEvent event) {
        if (machine.time().fleetLedger()) {
            entries.getOrDefault(event.type(), _ -> List.of()).apply(event).forEach(ledger::append);
        }
    }

    private List<LedgerEntry> firstDispatch(LoggedEvent event) {
        if (WorkerId.parse(event.text("worker")).incarnation() != 1) {
            return List.of();
        }
        Intake intake = transaction.execute(_ -> tasks.get(event.task()).intake());
        return List.of(new LedgerEntry.Dispatched(event.task(), event.at(), intake.kind().word(), intake.projects().getFirst().value(),
                event.text("model"), workKeyOf(intake)));
    }

    private static String workKeyOf(Intake intake) {
        return intake.workKey() == null ? null : intake.workKey().toString();
    }

    private static List<LedgerEntry> status(LoggedEvent event) {
        return List.of(new LedgerEntry.Status(event.task(), event.at(), event.text("state"), event.text("key"), event.text("text")));
    }

    private static List<LedgerEntry> draftReady(LoggedEvent event) {
        return List.of(new LedgerEntry.DraftReady(event.task(), event.at(), event.text("url")));
    }

    private static List<LedgerEntry> merged(LoggedEvent event) {
        return List.of(new LedgerEntry.Merged(event.task(), event.at(), event.text("url")));
    }

    private List<LedgerEntry> workedAndCleanedUp(LoggedEvent cleanup) {
        return Stream.concat(worked(cleanup).stream(), Stream.of(new LedgerEntry.CleanedUp(cleanup.task(), cleanup.at()))).toList();
    }

    private Optional<LedgerEntry> worked(LoggedEvent cleanup) {
        return transaction.execute(_ -> workedTimes.of(cleanup.task(), cleanup.at()).map(worked -> workedEntry(cleanup, worked)));
    }

    private LedgerEntry workedEntry(LoggedEvent cleanup, WorkedTime worked) {
        Intake intake = tasks.get(cleanup.task()).intake();
        return new LedgerEntry.Worked(cleanup.task(), cleanup.at(), workKeyOf(intake), worked.activeMinutes(), worked.wallClockMinutes());
    }
}
