package io.github.ythibos.edi.application.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.registry.UnreadableRegistry;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.tracker.TrackerUnavailable;
import io.github.ythibos.edi.application.tracker.Trackers;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.memory.TicketStanding;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class WorkaroundTickets {

    private final TaskStore tasks;
    private final ProjectRegistry registry;
    private final Trackers trackers;
    private final TransactionTemplate transaction;

    public WorkaroundTickets(TaskStore tasks, ProjectRegistry registry, Trackers trackers, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.registry = registry;
        this.trackers = trackers;
        this.transaction = transaction;
    }

    public Map<WorkKey, TicketStanding> fromTheStore(List<WorkKey> keys) {
        return readableProjects()
                .map(projects -> standingsOf(keys, key -> externalProjectOf(key, projects).isPresent() ? landedInTheStore(key) : inTheStore(key)))
                .orElseGet(Map::of);
    }

    public Map<WorkKey, TicketStanding> fromTheStoreAndTheTrackers(List<WorkKey> keys) {
        List<ProjectEntry> projects = registry.entries();
        return standingsOf(keys, key -> externalProjectOf(key, projects).map(project -> onTheTracker(project, key)).orElseGet(() -> inTheStore(key)));
    }

    private Optional<List<ProjectEntry>> readableProjects() {
        try {
            return Optional.of(registry.entries());
        } catch (UnreadableRegistry unreadable) {
            return Optional.empty();
        }
    }

    private static Map<WorkKey, TicketStanding> standingsOf(List<WorkKey> keys, Function<WorkKey, TicketStanding> standing) {
        Map<WorkKey, TicketStanding> standings = new LinkedHashMap<>();
        keys.forEach(key -> standings.put(key, standing.apply(key)));
        return standings;
    }

    private static Optional<ProjectEntry> externalProjectOf(WorkKey key, List<ProjectEntry> projects) {
        return projects.stream()
                .filter(project -> key.project().equals(project.project().workKeys()))
                .filter(project -> project.tracker().isExternal())
                .findFirst();
    }

    private TicketStanding landedInTheStore(WorkKey key) {
        return keyed(key).stream().anyMatch(Task::hasLanded) ? TicketStanding.LANDED : TicketStanding.UNCHECKED;
    }

    private TicketStanding inTheStore(WorkKey key) {
        List<Task> keyed = keyed(key);
        if (keyed.stream().anyMatch(Task::hasLanded)) {
            return TicketStanding.LANDED;
        }
        return keyed.stream().anyMatch(task -> !task.isClosed()) ? TicketStanding.OPEN : TicketStanding.UNTICKETED;
    }

    private List<Task> keyed(WorkKey key) {
        return transaction.execute(_ -> tasks.keyedOn(key));
    }

    private TicketStanding onTheTracker(ProjectEntry project, WorkKey key) {
        try {
            return trackers.serving(project).found(key)
                    .map(ticket -> trackers.hasLanded(ticket) ? TicketStanding.LANDED : TicketStanding.OPEN)
                    .orElse(TicketStanding.UNTICKETED);
        } catch (Refusal | TrackerUnavailable unanswered) {
            return TicketStanding.UNCHECKED;
        }
    }
}
