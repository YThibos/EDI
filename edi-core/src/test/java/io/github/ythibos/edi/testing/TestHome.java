package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.fork.ForkBacklogReader;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.StoreFile;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.decision.AnswerDecision;
import io.github.ythibos.edi.application.decision.DecisionListQuery;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.application.migration.ForkMigrationPlanner;
import io.github.ythibos.edi.application.task.AddTask;
import io.github.ythibos.edi.application.task.BacklogQuery;
import io.github.ythibos.edi.application.task.HoldTask;
import io.github.ythibos.edi.application.task.NoteTask;
import io.github.ythibos.edi.application.task.ReleaseTask;
import io.github.ythibos.edi.application.task.TaskDetailQuery;
import io.github.ythibos.edi.application.task.TaskListQuery;
import tools.jackson.databind.json.JsonMapper;

public final class TestHome {

    public static final Instant START = Instant.parse("2026-10-15T09:00:00Z");

    private final EdiHome home;
    private final ManualClock clock = new ManualClock(START);
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final JsonMapper json = new JsonMapper();

    private TestHome(EdiHome home, DataSource store) {
        this.home = home;
        this.jdbc = JdbcClient.create(store);
        this.transaction = new TransactionTemplate(new JdbcTransactionManager(store));
        this.tasks = new TaskStore(jdbc);
        this.decisions = new DecisionStore(jdbc);
        this.events = new EventLog(jdbc, json);
        this.registry = new ProjectRegistry(home);
    }

    public static TestHome in(Path directory) throws IOException {
        DataSource store = StoreFile.openedAt(new EdiHome(directory).store());
        Flyway.configure().dataSource(store).load().migrate();
        return new TestHome(new EdiHome(directory), store);
    }

    public TestHome registering(String projectsYaml) throws IOException {
        Files.createDirectories(home.projectRegistry().getParent());
        Files.writeString(home.projectRegistry(), projectsYaml);
        return this;
    }

    public TestHome continuingDecisionsFrom(int highestNumberSoFar) {
        jdbc.sql("update meta set value = :highest where name = 'decision_seq'").param("highest", highestNumberSoFar).update();
        return this;
    }

    public ManualClock clock() {
        return clock;
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    public TaskStore tasks() {
        return tasks;
    }

    public DecisionStore decisions() {
        return decisions;
    }

    public EventLog events() {
        return events;
    }

    public ProjectRegistry registry() {
        return registry;
    }

    public AddTask addTask() {
        return new AddTask(tasks, events, registry, clock, transaction);
    }

    public NoteTask noteTask() {
        return new NoteTask(tasks, events, clock, transaction);
    }

    public HoldTask holdTask() {
        return new HoldTask(tasks, events, clock, transaction);
    }

    public ReleaseTask releaseTask() {
        return new ReleaseTask(tasks, events, clock, transaction);
    }

    public RaiseDecision raiseDecision() {
        return new RaiseDecision(tasks, decisions, events, clock, transaction);
    }

    public AnswerDecision answerDecision() {
        return new AnswerDecision(tasks, decisions, events, clock, transaction);
    }

    public BacklogQuery backlog() {
        return new BacklogQuery(tasks, transaction);
    }

    public TaskListQuery taskList() {
        return new TaskListQuery(tasks, transaction);
    }

    public TaskDetailQuery taskDetail() {
        return new TaskDetailQuery(tasks, decisions, events, transaction);
    }

    public DecisionListQuery decisionList() {
        return new DecisionListQuery(decisions, transaction);
    }

    public ForkMigrationPlanner forkMigrationPlanner() {
        return new ForkMigrationPlanner(new ForkBacklogReader(json), clock);
    }
}
