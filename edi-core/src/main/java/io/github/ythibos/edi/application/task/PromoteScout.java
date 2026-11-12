package io.github.ythibos.edi.application.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.ScoutPromoted;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class PromoteScout {

    private static final String REPORT_FILE = "report.md";

    private final TaskStore tasks;
    private final AddTask addTask;
    private final EventLog events;
    private final CaptainsWordEvidence captainsWord;
    private final EdiHome home;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PromoteScout(TaskStore tasks, AddTask addTask, EventLog events, CaptainsWordEvidence captainsWord, EdiHome home, Clock clock,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.addTask = addTask;
        this.events = events;
        this.captainsWord = captainsWord;
        this.home = home;
        this.clock = clock;
        this.transaction = transaction;
    }

    public PromotedScout promote(TaskReference reference, String shipTitle, CallingSession calling, Actor actor) {
        Task scout = transaction.execute(_ -> tasks.get(reference));
        requireAnOpenScout(scout);
        CaptainsWord word = captainsWord.requireOnTask(calling, scout.id(), CaptainWordAct.PROMOTE);
        AddedTask ship = addTask.add(new NewTask(null, shipIntakeOf(scout, shipTitle), shipNotesOf(scout, word)), actor);
        transaction.executeWithoutResult(_ -> events.record(new ScoutPromoted(scout.id(), ship.id()), actor, clock.now()));
        return new PromotedScout(scout.id(), ship);
    }

    private static void requireAnOpenScout(Task task) {
        if (task.intake().kind() != TaskKind.SCOUT) {
            throw new Refusal(Rule.TASK_STAGE, task.id() + " is a " + task.intake().kind().word() + "; only a scout is promoted to a ship.");
        }
        if (task.state() == TaskState.DROPPED) {
            throw new Refusal(Rule.TASK_STAGE, task.id() + " was dropped, so it is not promoted.");
        }
    }

    private static Intake shipIntakeOf(Task scout, String shipTitle) {
        Intake researched = scout.intake();
        String title = shipTitle == null || shipTitle.isBlank() ? researched.title() : shipTitle;
        return new Intake(TaskKind.SHIP, title, researched.projects(), researched.workKey(), researched.base(), List.of(), researched.delivery());
    }

    private String shipNotesOf(Task scout, CaptainsWord word) {
        String promotion = "Promoted from the scout " + scout.id() + " on the captain's word: \"" + word.verbatim() + "\"." + reportLineOf(scout);
        return scout.notes().isEmpty() ? promotion : scout.notes() + "\n\n" + promotion;
    }

    private String reportLineOf(Task scout) {
        Path report = home.taskDirectory(scout.id()).resolve(REPORT_FILE);
        return Files.isRegularFile(report) ? "\nThe scout's report: " + report : "";
    }
}
