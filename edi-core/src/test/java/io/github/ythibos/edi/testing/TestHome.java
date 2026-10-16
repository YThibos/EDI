package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.build.ProjectBuild;
import io.github.ythibos.edi.adapter.fork.ForkBacklogReader;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.home.DispatchRulesFile;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.machine.MachineBoot;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.NoticeStore;
import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.adapter.store.RecoveryCostStore;
import io.github.ythibos.edi.adapter.store.StoreFile;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WakeStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.brief.WorkerBriefs;
import io.github.ythibos.edi.application.decision.AnswerDecision;
import io.github.ythibos.edi.application.decision.DecisionListQuery;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.application.dispatch.DispatchTask;
import io.github.ythibos.edi.application.dispatch.IsolatedCopies;
import io.github.ythibos.edi.application.dispatch.LaunchDeferredDispatches;
import io.github.ythibos.edi.application.dispatch.LaunchWorker;
import io.github.ythibos.edi.application.dispatch.ResumeNotes;
import io.github.ythibos.edi.application.dispatch.WorkerPlaces;
import io.github.ythibos.edi.application.dispatch.WorktreeHooks;
import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.migration.ForkMigrationPlanner;
import io.github.ythibos.edi.application.primary.PrimaryHooks;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.application.primary.SessionStartDigestQuery;
import io.github.ythibos.edi.application.supervision.CurrentRoutes;
import io.github.ythibos.edi.application.supervision.JudgeWorkers;
import io.github.ythibos.edi.application.supervision.NoticeDueHolds;
import io.github.ythibos.edi.application.supervision.OutageHoldList;
import io.github.ythibos.edi.application.supervision.OutageListQuery;
import io.github.ythibos.edi.application.supervision.RecoverFromOutage;
import io.github.ythibos.edi.application.supervision.ResumeAfterUsageLimits;
import io.github.ythibos.edi.application.task.AddTask;
import io.github.ythibos.edi.application.task.BacklogQuery;
import io.github.ythibos.edi.application.task.HoldTask;
import io.github.ythibos.edi.application.task.NoteTask;
import io.github.ythibos.edi.application.task.ReleaseTask;
import io.github.ythibos.edi.application.task.TaskDetailQuery;
import io.github.ythibos.edi.application.task.TaskListQuery;
import io.github.ythibos.edi.application.wake.WaitForWakes;
import io.github.ythibos.edi.application.worker.CaptainCheckouts;
import io.github.ythibos.edi.application.worker.CheckMechanicalChange;
import io.github.ythibos.edi.application.worker.CountRecoveryCost;
import io.github.ythibos.edi.application.worker.GuardToolCall;
import io.github.ythibos.edi.application.worker.NoticeMissedLaunches;
import io.github.ythibos.edi.application.worker.NoticeOverdueSteers;
import io.github.ythibos.edi.application.worker.RecordStatus;
import io.github.ythibos.edi.application.worker.RelaunchWorker;
import io.github.ythibos.edi.application.worker.SteerWorker;
import io.github.ythibos.edi.application.worker.StopWorker;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerHooks;
import io.github.ythibos.edi.application.worker.WorkerInbox;
import io.github.ythibos.edi.application.worker.WorkerListQuery;
import io.github.ythibos.edi.application.worker.WorkerTokens;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import tools.jackson.databind.json.JsonMapper;

public final class TestHome {

    public static final Instant START = Instant.parse("2026-10-15T09:00:00Z");

    private final EdiHome home;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final JsonMapper json = new JsonMapper();
    private final DispatchStore dispatches;
    private final WorkerStore workers;
    private final InboxStore inbox;
    private final FakeWorkerRuntime runtime;
    private final ManualClock clock;
    private final ExternalPrograms programs = new ExternalPrograms();
    private final Git git = new Git(programs);
    private final ProcessTable processes = new ProcessTable();
    private final WakeStore wakes;
    private final PrimarySessionStore primarySessions;
    private final NoticeStore notices;
    private final OutageStore outages;
    private final RecoveryCostStore recoveryCosts;

    private TestHome(EdiHome home, DataSource store, FakeWorkerRuntime runtime, ManualClock clock) {
        this.home = home;
        this.runtime = runtime;
        this.clock = clock;
        this.jdbc = JdbcClient.create(store);
        this.transaction = new TransactionTemplate(new JdbcTransactionManager(store));
        this.tasks = new TaskStore(jdbc);
        this.decisions = new DecisionStore(jdbc);
        this.wakes = new WakeStore(jdbc);
        this.events = new EventLog(jdbc, json, wakes, _ -> { });
        this.registry = new ProjectRegistry(home);
        this.dispatches = new DispatchStore(jdbc);
        this.workers = new WorkerStore(jdbc);
        this.inbox = new InboxStore(jdbc);
        this.primarySessions = new PrimarySessionStore(jdbc);
        this.notices = new NoticeStore(jdbc);
        this.outages = new OutageStore(jdbc);
        this.recoveryCosts = new RecoveryCostStore(jdbc);
    }

    public static TestHome in(Path directory) throws IOException {
        return openedIn(directory, new FakeWorkerRuntime(), new ManualClock(START));
    }

    public TestHome restarted() throws IOException {
        return openedIn(home.root(), runtime, clock);
    }

    public void restartTheMachine() throws IOException {
        Files.writeString(bootIdFile(home.root()), UUID.randomUUID().toString());
        runtime.loseTheServer();
    }

    private static TestHome openedIn(Path directory, FakeWorkerRuntime runtime, ManualClock clock) throws IOException {
        DataSource store = StoreFile.openedAt(new EdiHome(directory).store());
        Flyway.configure().dataSource(store).load().migrate();
        if (!Files.exists(bootIdFile(directory))) {
            Files.writeString(bootIdFile(directory), UUID.randomUUID().toString());
        }
        return new TestHome(new EdiHome(directory), store, runtime, clock);
    }

    private static Path bootIdFile(Path directory) {
        return directory.resolve("boot_id");
    }

    public TestHome registering(String projectsYaml) throws IOException {
        Files.createDirectories(home.projectRegistry().getParent());
        Files.writeString(home.projectRegistry(), projectsYaml);
        return this;
    }

    public TestHome writing(String file, String content) throws IOException {
        Path written = home.root().resolve(file);
        Files.createDirectories(written.getParent());
        Files.writeString(written, content);
        return this;
    }

    public TestHome storing(List<Task> fixtureTasks) {
        transaction.executeWithoutResult(_ -> fixtureTasks.forEach(tasks::insert));
        return this;
    }

    public void putInFlight(TaskId id) {
        Task task = tasks.get(id);
        tasks.update(new Task(task.id(), task.intake(), TaskState.IN_FLIGHT, task.notes(), task.hold(), task.created(), null));
    }

    public void markDone(TaskId id) {
        Task task = tasks.get(id);
        tasks.update(new Task(task.id(), task.intake(), TaskState.DONE, task.notes(), null, task.created(), clock.now()));
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

    public FleetSnapshotQuery fleetSnapshot() {
        return new FleetSnapshotQuery(tasks, decisions, transaction);
    }

    public ForkMigrationPlanner forkMigrationPlanner() {
        return new ForkMigrationPlanner(new ForkBacklogReader(json), clock);
    }

    public EdiHome home() {
        return home;
    }

    public FakeWorkerRuntime runtime() {
        return runtime;
    }

    public WorkerStore workers() {
        return workers;
    }

    public DispatchStore dispatches() {
        return dispatches;
    }

    public InboxStore inbox() {
        return inbox;
    }

    public Git git() {
        return git;
    }

    public MachineProfile machineProfile() {
        return new MachineProfile(home);
    }

    public DispatchRulesFile dispatchRulesFile() {
        return new DispatchRulesFile(home, json);
    }

    public IsolatedCopies isolatedCopies() {
        return new IsolatedCopies(home, git, new WorktreeHooks(git));
    }

    public WorkerPlaces workerPlaces() {
        return new WorkerPlaces(workers, tasks, runtime);
    }

    public WorkerTokens workerTokens() {
        return new WorkerTokens(workers, transaction);
    }

    public LaunchWorker launchWorker() {
        return new LaunchWorker(home, tasks, dispatches, workers, outages, events, registry, machineProfile(), dispatchRulesFile(), isolatedCopies(),
                workerPlaces(), new WorkerBriefs(), new ResumeNotes(git, inbox, decisions, transaction), workerTokens(), runtime, processes,
                machineBoot(), clock, transaction);
    }

    public DispatchTask dispatchTask() {
        return new DispatchTask(tasks, dispatches, events, registry, machineProfile(), dispatchRulesFile(), workerPlaces(), launchWorker(),
                clock, transaction);
    }

    public LaunchDeferredDispatches launchDeferredDispatches() {
        return new LaunchDeferredDispatches(dispatches, tasks, registry, workerPlaces(), launchWorker(), events, clock, transaction);
    }

    public WorkerContext workerContext() {
        return new WorkerContext(home, tasks, dispatches, registry);
    }

    public CaptainCheckouts captainCheckouts() {
        return new CaptainCheckouts(workerContext(), git, events, clock, transaction);
    }

    public RecordStatus recordStatus() {
        return new RecordStatus(workers, events, new PersonalConfiguration(home), captainCheckouts(), clock, transaction);
    }

    public WorkerInbox workerInbox() {
        return new WorkerInbox(inbox, events, clock, transaction);
    }

    public SteerWorker steerWorker() {
        return new SteerWorker(tasks, workers, inbox, events, runtime, clock, transaction);
    }

    public WorkerHooks workerHooks() {
        return new WorkerHooks(workers, inbox, events, runtime, countRecoveryCost(), clock, transaction);
    }

    public GuardToolCall guardToolCall() {
        return new GuardToolCall(workerContext(), registry, runtime, events, clock, transaction);
    }

    public NoticeOverdueSteers noticeOverdueSteers() {
        return new NoticeOverdueSteers(inbox, events, clock, transaction);
    }

    public NoticeMissedLaunches noticeMissedLaunches() {
        return new NoticeMissedLaunches(workers, runtime, events, clock, transaction);
    }

    public MachineBoot machineBoot() {
        return new MachineBoot(bootIdFile(home.root()));
    }

    public ProcessTable processes() {
        return processes;
    }

    public WakeStore wakes() {
        return wakes;
    }

    public PrimarySessionStore primarySessionStore() {
        return primarySessions;
    }

    public NoticeStore notices() {
        return notices;
    }

    public OutageStore outages() {
        return outages;
    }

    public PrimarySessions primarySessions() {
        return new PrimarySessions(primarySessions, processes, transaction);
    }

    public PrimaryHooks primaryHooks() {
        return new PrimaryHooks(primarySessions, primarySessions(), tasks, events, processes, runtime, clock, transaction);
    }

    public WorkerListQuery workerList() {
        return new WorkerListQuery(workers, runtime, machineBoot(), machineProfile(), clock, transaction);
    }

    public SessionStartDigestQuery sessionStartDigest() {
        return new SessionStartDigestQuery(primarySessions(), primarySessions, fleetSnapshot(), workerList(), outages, wakes, transaction);
    }

    public WaitForWakes waitForWakes() {
        return new WaitForWakes(wakes, primarySessions(), decisions, tasks, outages, clock, transaction);
    }

    public CountRecoveryCost countRecoveryCost() {
        return new CountRecoveryCost(recoveryCosts, events, runtime, clock, transaction);
    }

    public OutageHoldList outageHoldList() {
        return new OutageHoldList(outages, tasks, notices, events, workerList(), clock, transaction);
    }

    public OutageListQuery outageList() {
        return new OutageListQuery(outages, tasks, workers, dispatches, decisions, inbox, recoveryCosts,
                new CurrentRoutes(dispatchRulesFile(), machineProfile()), git, home, transaction);
    }

    public JudgeWorkers judgeWorkers() {
        return new JudgeWorkers(workerList(), inbox, notices, events, outageHoldList(), countRecoveryCost(), machineProfile(), clock, transaction);
    }

    public RecoverFromOutage recoverFromOutage() {
        return new RecoverFromOutage(outages, tasks, dispatches, primarySessions(), workerList(),
                new CurrentRoutes(dispatchRulesFile(), machineProfile()), launchWorker(), events, clock, transaction);
    }

    public RelaunchWorker relaunchWorker() {
        return new RelaunchWorker(tasks, workers, workerList(), launchWorker(), transaction);
    }

    public StopWorker stopWorker() {
        return new StopWorker(tasks, workers, runtime, processes, events, clock, transaction);
    }

    public ResumeAfterUsageLimits resumeAfterUsageLimits() {
        return new ResumeAfterUsageLimits(workerList(), runtime, notices, events, clock, transaction);
    }

    public NoticeDueHolds noticeDueHolds() {
        return new NoticeDueHolds(tasks, notices, events, clock, transaction);
    }

    public CheckMechanicalChange checkMechanicalChange() {
        return new CheckMechanicalChange(workers, workerContext(), git, new ProjectBuild(programs), runtime, launchWorker(), captainCheckouts(),
                events, clock, transaction);
    }

    public List<String> eventTypesOf(TaskId task) {
        return jdbc.sql("select type from event where task_id = :task order by sequence").param("task", task.value()).query(String.class).list();
    }
}
