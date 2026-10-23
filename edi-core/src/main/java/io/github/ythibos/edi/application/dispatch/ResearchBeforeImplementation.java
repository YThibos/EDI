package io.github.ythibos.edi.application.dispatch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.UnplannedShipStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.UnplannedShip;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.ResearchQueuedFirst;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

@Service
public class ResearchBeforeImplementation {

    private static final String REPORT_FILE = "report.md";

    private final EdiHome home;
    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final UnplannedShipStore unplannedShips;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ResearchBeforeImplementation(EdiHome home, TaskStore tasks, DispatchStore dispatches, UnplannedShipStore unplannedShips, EventLog events,
            Clock clock, TransactionTemplate transaction) {
        this.home = home;
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.unplannedShips = unplannedShips;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public TaskId queueThePlanOf(Dispatch shipDispatch, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Task ship = tasks.get(shipDispatch.task());
            Task research = researchTaskFor(ship, now);
            tasks.insert(research);
            events.record(new TaskAdded(research.id(), research.intake()), actor, now);
            tasks.update(ship.waitingFor(research.id()));
            dispatches.save(shipDispatch.asImplementationOfItsPlan());
            unplannedShips.insert(new UnplannedShip(ship.id(), shipDispatch.category(), research.id(), now));
            events.record(new ResearchQueuedFirst(ship.id(), research.id(), shipDispatch.category()), actor, now);
            return research.id();
        });
    }

    public DispatchRequest researchRequestFor(Dispatch shipDispatch) {
        return new DispatchRequest(CategoryId.RESEARCH, researchSpecFor(shipDispatch), List.of(), null);
    }

    public void countWithoutResearch(Dispatch shipDispatch) {
        transaction.executeWithoutResult(_ -> {
            if (unplannedShips.of(shipDispatch.task()).isEmpty()) {
                unplannedShips.insert(new UnplannedShip(shipDispatch.task(), shipDispatch.category(), null, clock.now()));
            }
        });
    }

    public Optional<String> planFor(TaskId ship) {
        return researchPlanning(ship).map(this::planWrittenBy);
    }

    public Optional<Path> planReportFor(TaskId ship) {
        return researchPlanning(ship).map(this::reportOf);
    }

    private Task researchTaskFor(Task ship, Instant now) {
        Intake shipIntake = ship.intake();
        Intake intake = new Intake(TaskKind.SCOUT, "Plan: " + shipIntake.title(), shipIntake.projects(), shipIntake.workKey(), shipIntake.base(),
                List.of(), null);
        return Task.queued(tasks.firstFreeIdFrom(TaskId.sluggedFrom("plan " + shipIntake.title())), intake, ship.notes(), now);
    }

    private static String researchSpecFor(Dispatch shipDispatch) {
        String plan = String.join("\n",
                "Research before implementation: plan the ship `" + shipDispatch.task() + "` so that an implementation worker builds it without"
                        + " designing anything further.",
                "Your report is that plan: numbered steps naming the files, classes and tests each step changes, the full acceptance criteria"
                        + " the ship must meet, and how each criterion is verified.",
                "When a choice in the plan belongs to the captain, report it with `needs-decision` and its options before you finish the plan.");
        return shipDispatch.spec().isBlank() ? plan : plan + "\n\nThe ship's own build instructions, which the plan refines:\n\n" + shipDispatch.spec();
    }

    private Optional<TaskId> researchPlanning(TaskId ship) {
        return transaction.execute(_ -> unplannedShips.of(ship)).filter(UnplannedShip::isPiloted).map(UnplannedShip::research);
    }

    private String planWrittenBy(TaskId research) {
        Path report = reportOf(research);
        try {
            return "Follow the plan that research task `" + research + "` settled; its report follows verbatim.\n\n"
                    + Files.readString(report, StandardCharsets.UTF_8).strip();
        } catch (NoSuchFileException absent) {
            throw new Refusal("The plan of " + research + " is not written yet: " + report + " is missing.");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private Path reportOf(TaskId research) {
        return home.taskDirectory(research).resolve(REPORT_FILE);
    }
}
