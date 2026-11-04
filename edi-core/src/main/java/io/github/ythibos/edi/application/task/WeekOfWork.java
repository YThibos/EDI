package io.github.ythibos.edi.application.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.WorkMark;
import io.github.ythibos.edi.domain.task.WorkedTime;

@Service
public class WeekOfWork {

    private static final int DAYS = 7;
    private static final String TOOLING = "tooling";

    private final TaskStore tasks;
    private final WorkedTimes workedTimes;
    private final ProjectRegistry registry;
    private final MachineProfile machine;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WeekOfWork(TaskStore tasks, WorkedTimes workedTimes, ProjectRegistry registry, MachineProfile machine, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.workedTimes = workedTimes;
        this.registry = registry;
        this.machine = machine;
        this.clock = clock;
        this.transaction = transaction;
    }

    public WorkedWeek from(LocalDate first) {
        ZoneId zone = clock.zone();
        List<LocalDate> days = IntStream.range(0, DAYS).mapToObj(first::plusDays).toList();
        Instant now = clock.now();
        return transaction.execute(_ -> new WorkedWeek(first, zone, days, tasks.ids().stream()
                .map(tasks::get)
                .flatMap(task -> workedIn(task, days, zone, now).stream())
                .toList()));
    }

    private Optional<WorkedWeek.WorkedTask> workedIn(Task task, List<LocalDate> days, ZoneId zone, Instant now) {
        Instant until = task.isClosed() ? task.closed() : now;
        List<WorkMark> marks = workedTimes.workMarksOf(task.id());
        List<WorkedWeek.WorkedDay> worked = days.stream()
                .flatMap(day -> workedOn(marks, day, zone, until).stream())
                .toList();
        if (worked.stream().noneMatch(day -> day.activeMinutes() > 0)) {
            return Optional.empty();
        }
        WorkKey key = task.intake().workKey();
        return Optional.of(new WorkedWeek.WorkedTask(task.id(), task.intake().title(), key, bookedOn(task, key), worked));
    }

    private static Optional<WorkedWeek.WorkedDay> workedOn(List<WorkMark> marks, LocalDate day, ZoneId zone, Instant until) {
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        return WorkedTime.within(marks, until, from, to)
                .filter(worked -> !worked.isNothing())
                .map(worked -> new WorkedWeek.WorkedDay(day, worked.activeMinutes(), worked.wallClockMinutes()));
    }

    private WorkKey bookedOn(Task task, WorkKey key) {
        if (key == null || bookedOnItsOwnTicket(task)) {
            return key;
        }
        return machine.tracker().evergreenTicket(TOOLING).orElse(key);
    }

    private boolean bookedOnItsOwnTicket(Task task) {
        return task.intake().projects().stream().findFirst()
                .map(project -> registry.entryOf(project).tracker().isExternal())
                .orElse(true);
    }
}
