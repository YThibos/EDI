package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.browser.CaptainsBrowser;
import io.github.ythibos.edi.adapter.build.ProjectBuild;
import io.github.ythibos.edi.adapter.fork.ForkBacklogReader;
import io.github.ythibos.edi.adapter.fork.ForkEnvironment;
import io.github.ythibos.edi.adapter.fork.ForkRoutingRulesReader;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.home.DispatchRulesFile;
import io.github.ythibos.edi.adapter.home.HomeSetup;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.MemoryFiles;
import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.home.PersonalLayer;
import io.github.ythibos.edi.adapter.ledger.FleetLedger;
import io.github.ythibos.edi.adapter.machine.MachineBoot;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.runtime.ClaudeLoginFiles;
import io.github.ythibos.edi.adapter.runtime.ClaudeSessions;
import io.github.ythibos.edi.adapter.store.AdoptionStore;
import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.CaptainMessageStore;
import io.github.ythibos.edi.adapter.store.CaptainWordStore;
import io.github.ythibos.edi.adapter.store.CloneRefreshStore;
import io.github.ythibos.edi.adapter.store.ConsoleAccessStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventCursorStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.MemoryCheckStore;
import io.github.ythibos.edi.adapter.store.NoticeStore;
import io.github.ythibos.edi.adapter.store.OneTransactionAtATime;
import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.adapter.store.PipelineReviewStore;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.adapter.store.RecoveryCostStore;
import io.github.ythibos.edi.adapter.store.SparkStore;
import io.github.ythibos.edi.adapter.store.TaskCostStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.TicketProposalStore;
import io.github.ythibos.edi.adapter.store.UnplannedShipStore;
import io.github.ythibos.edi.adapter.store.WakeStore;
import io.github.ythibos.edi.adapter.store.WakeStreamStore;
import io.github.ythibos.edi.adapter.store.WorkKeySequenceStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.adapter.tracker.EdiNativeTracker;
import io.github.ythibos.edi.adapter.tracker.GitHubIssuesTracker;
import io.github.ythibos.edi.adapter.tracker.NoTracker;
import io.github.ythibos.edi.adapter.windows.OutlookCalendar;
import io.github.ythibos.edi.adapter.windows.WindowsToasts;
import io.github.ythibos.edi.application.access.ConsoleAccess;
import io.github.ythibos.edi.application.access.ConsoleHandOffs;
import io.github.ythibos.edi.application.access.OpenConsole;
import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.board.ArmBoard;
import io.github.ythibos.edi.application.board.PostBoardRound;
import io.github.ythibos.edi.application.board.BoardListeners;
import io.github.ythibos.edi.application.board.BoardRoundsQuery;
import io.github.ythibos.edi.application.board.ReceiveBoardFeedback;
import io.github.ythibos.edi.application.board.SettleBoardAnswers;
import io.github.ythibos.edi.application.brief.WorkerBriefs;
import io.github.ythibos.edi.application.brief.WrittenBriefs;
import io.github.ythibos.edi.application.decision.AnswerDecision;
import io.github.ythibos.edi.application.decision.AskForARecheck;
import io.github.ythibos.edi.application.decision.CloseDecisionRounds;
import io.github.ythibos.edi.application.decision.DecisionListQuery;
import io.github.ythibos.edi.application.decision.DecisionRoundQuery;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.application.delivery.CaptainCheckouts;
import io.github.ythibos.edi.application.delivery.CleanUpTask;
import io.github.ythibos.edi.application.delivery.CloneRefreshes;
import io.github.ythibos.edi.application.delivery.CopyRetirement;
import io.github.ythibos.edi.application.delivery.DeliverAcceptedWork;
import io.github.ythibos.edi.application.delivery.DeliverDraft;
import io.github.ythibos.edi.application.delivery.DeliveryRecords;
import io.github.ythibos.edi.application.delivery.DiscardWork;
import io.github.ythibos.edi.application.delivery.DoneClaims;
import io.github.ythibos.edi.application.delivery.DropTask;
import io.github.ythibos.edi.application.delivery.FollowMerges;
import io.github.ythibos.edi.application.delivery.Forges;
import io.github.ythibos.edi.application.delivery.LandedWorkCheck;
import io.github.ythibos.edi.application.delivery.ObservePipelines;
import io.github.ythibos.edi.application.delivery.RecountTaskCosts;
import io.github.ythibos.edi.application.delivery.TaskCosts;
import io.github.ythibos.edi.application.dispatch.AwayLimit;
import io.github.ythibos.edi.application.dispatch.DispatchTask;
import io.github.ythibos.edi.application.dispatch.GatherBriefFacts;
import io.github.ythibos.edi.application.dispatch.Handovers;
import io.github.ythibos.edi.application.dispatch.IsolatedCopies;
import io.github.ythibos.edi.application.dispatch.LaunchDeferredDispatches;
import io.github.ythibos.edi.application.dispatch.LaunchWorker;
import io.github.ythibos.edi.application.dispatch.ResearchBeforeImplementation;
import io.github.ythibos.edi.application.dispatch.ResearchFirstPilot;
import io.github.ythibos.edi.application.dispatch.ResumeNotes;
import io.github.ythibos.edi.application.dispatch.RewriteBrief;
import io.github.ythibos.edi.application.dispatch.WorkerPlaces;
import io.github.ythibos.edi.application.dispatch.WorktreeHooks;
import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.fleet.TaskLinksQuery;
import io.github.ythibos.edi.application.follow.EventFollowers;
import io.github.ythibos.edi.application.ledger.EmitFleetLedger;
import io.github.ythibos.edi.application.lifecycle.StopTheCore;
import io.github.ythibos.edi.application.memory.DailyMemoryCheck;
import io.github.ythibos.edi.application.memory.MemoryQuery;
import io.github.ythibos.edi.application.memory.WorkaroundTickets;
import io.github.ythibos.edi.application.migration.AdoptForkTask;
import io.github.ythibos.edi.application.migration.BacklogImport;
import io.github.ythibos.edi.application.migration.ExampleConfiguration;
import io.github.ythibos.edi.application.migration.ForkMigrationPlanner;
import io.github.ythibos.edi.application.migration.HomeImportPlanner;
import io.github.ythibos.edi.application.migration.HomeWriter;
import io.github.ythibos.edi.application.migration.MigrateFirstofficer;
import io.github.ythibos.edi.application.personality.ChangePersonality;
import io.github.ythibos.edi.application.personality.PersonalityQuery;
import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.presence.ChangePresence;
import io.github.ythibos.edi.application.presence.FollowThePresence;
import io.github.ythibos.edi.application.presence.MeetingCalendar;
import io.github.ythibos.edi.application.presence.NoticeTheUnreadableCalendar;
import io.github.ythibos.edi.application.presence.NotifyTheCaptain;
import io.github.ythibos.edi.application.presence.PresenceQuery;
import io.github.ythibos.edi.application.presence.ReturnBriefQuery;
import io.github.ythibos.edi.application.primary.OpenTheConsoleOnRequest;
import io.github.ythibos.edi.application.primary.OpenTheSitrepAtSessionStart;
import io.github.ythibos.edi.application.primary.PrepareThePrimary;
import io.github.ythibos.edi.application.primary.PrimaryHooks;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.application.primary.PrimaryWaitQuery;
import io.github.ythibos.edi.application.primary.SessionStartDigestQuery;
import io.github.ythibos.edi.application.publish.PublicationTargets;
import io.github.ythibos.edi.application.publish.PublishCheck;
import io.github.ythibos.edi.application.spark.CatchSpark;
import io.github.ythibos.edi.application.spark.SortSpark;
import io.github.ythibos.edi.application.supervision.CurrentRoutes;
import io.github.ythibos.edi.application.supervision.DropFromOutage;
import io.github.ythibos.edi.application.supervision.JudgeWorkers;
import io.github.ythibos.edi.application.supervision.NoticeDueHolds;
import io.github.ythibos.edi.application.supervision.OutageHoldList;
import io.github.ythibos.edi.application.supervision.OutageListQuery;
import io.github.ythibos.edi.application.supervision.RecoverFromOutage;
import io.github.ythibos.edi.application.supervision.ResumeAfterUsageLimits;
import io.github.ythibos.edi.application.task.AddTask;
import io.github.ythibos.edi.application.task.BacklogQuery;
import io.github.ythibos.edi.application.task.DependantTasks;
import io.github.ythibos.edi.application.task.HoldTask;
import io.github.ythibos.edi.application.task.NoteTask;
import io.github.ythibos.edi.application.task.PromoteScout;
import io.github.ythibos.edi.application.task.ReleaseTask;
import io.github.ythibos.edi.application.task.TaskDetailQuery;
import io.github.ythibos.edi.application.task.TaskListQuery;
import io.github.ythibos.edi.application.task.WeekOfWork;
import io.github.ythibos.edi.application.task.WorkedTimes;
import io.github.ythibos.edi.application.tracker.FollowTickets;
import io.github.ythibos.edi.application.tracker.KeyTask;
import io.github.ythibos.edi.application.tracker.TaskKeys;
import io.github.ythibos.edi.application.tracker.TicketProposals;
import io.github.ythibos.edi.application.tracker.Trackers;
import io.github.ythibos.edi.application.wake.WaitForWakes;
import io.github.ythibos.edi.application.wake.WakeStreams;
import io.github.ythibos.edi.application.worker.OpenWorkerQuestionsQuery;
import io.github.ythibos.edi.application.worker.RemindOfUnansweredQuestions;
import io.github.ythibos.edi.application.worker.CheckMechanicalChange;
import io.github.ythibos.edi.application.worker.CountRecoveryCost;
import io.github.ythibos.edi.application.worker.GuardToolCall;
import io.github.ythibos.edi.application.worker.InboxNotice;
import io.github.ythibos.edi.application.worker.NoticeFullContexts;
import io.github.ythibos.edi.application.worker.NoticeHeavyRunsAtOnce;
import io.github.ythibos.edi.application.worker.NoticeMissedStops;
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
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

public final class TestHome {

    public static final Instant START = Instant.parse("2026-10-15T09:00:00Z");

    private final EdiHome home;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final MachineProfile machineProfile;
    private final JsonMapper json = new JsonMapper();
    private final DispatchStore dispatches;
    private final WorkerStore workers;
    private final InboxStore inbox;
    private final FakeWorkerRuntime runtime;
    private final ManualClock clock;
    private final ConsoleHandOffs consoleHandOffs;
    private final ExternalPrograms programs = new ExternalPrograms();
    private final Git git = new Git(programs);
    private final ProcessTable processes = new ProcessTable();
    private final WakeStore wakes;
    private final PrimarySessionStore primarySessions;
    private final NoticeStore notices;
    private final OutageStore outages;
    private final RecoveryCostStore recoveryCosts;
    private final DeliveryStore deliveries;
    private final PipelineRunStore pipelineRuns;
    private final CloneRefreshStore cloneRefreshes;
    private final TaskCostStore taskCosts;
    private final FakeForge forge;
    private final FakeForge gitLabForge;
    private final FakePipeline pipeline = new FakePipeline();
    private final FakeJira jira = new FakeJira();
    private Tracker jiraAdapter = jira;
    private final WorkKeySequenceStore workKeySequences;
    private final TicketProposalStore ticketProposals;
    private final EventCursorStore eventCursors;
    private final UnplannedShipStore unplannedShips;
    private final PipelineReviewStore reviews;
    private final BoardStore boards;
    private final CaptainWordStore captainWords;
    private final CaptainMessageStore captainMessages;
    private final SparkStore sparks;
    private final PresenceStore presence;
    private final AdoptionStore adoptions;
    private String oldPauseResurfaceSeconds = "";
    private final FakeBoardHost boardHost = new FakeBoardHost();
    private WindowsStandIn windowsSide;
    private MeetingCalendar meetingCalendar;
    private BoardListeners boardListeners;
    private NoticeHeavyRunsAtOnce heavyRunsAtOnce;

    private TestHome(EdiHome home, DataSource store, FakeWorkerRuntime runtime, ManualClock clock) {
        this.home = home;
        this.runtime = runtime;
        this.clock = clock;
        this.consoleHandOffs = new ConsoleHandOffs(clock);
        this.forge = new FakeForge(ForgeKind.GITHUB, clock);
        this.gitLabForge = new FakeForge(ForgeKind.GITLAB, clock);
        this.jdbc = JdbcClient.create(store);
        this.transaction = new TransactionTemplate(new OneTransactionAtATime(store));
        this.tasks = new TaskStore(jdbc);
        this.decisions = new DecisionStore(jdbc);
        this.wakes = new WakeStore(jdbc);
        this.events = new EventLog(jdbc, json, wakes, _ -> { });
        this.machineProfile = new MachineProfile(home);
        this.registry = new ProjectRegistry(home, machineProfile);
        this.dispatches = new DispatchStore(jdbc);
        this.workers = new WorkerStore(jdbc);
        this.inbox = new InboxStore(jdbc);
        this.primarySessions = new PrimarySessionStore(jdbc);
        this.notices = new NoticeStore(jdbc);
        this.outages = new OutageStore(jdbc);
        this.recoveryCosts = new RecoveryCostStore(jdbc);
        this.deliveries = new DeliveryStore(jdbc);
        this.pipelineRuns = new PipelineRunStore(jdbc);
        this.cloneRefreshes = new CloneRefreshStore(jdbc);
        this.taskCosts = new TaskCostStore(jdbc);
        this.workKeySequences = new WorkKeySequenceStore(jdbc);
        this.ticketProposals = new TicketProposalStore(jdbc);
        this.eventCursors = new EventCursorStore(jdbc);
        this.unplannedShips = new UnplannedShipStore(jdbc);
        this.reviews = new PipelineReviewStore(jdbc);
        this.boards = new BoardStore(jdbc);
        this.captainWords = new CaptainWordStore(jdbc);
        this.captainMessages = new CaptainMessageStore(jdbc);
        this.sparks = new SparkStore(jdbc);
        this.presence = new PresenceStore(jdbc, _ -> { });
        this.adoptions = new AdoptionStore(jdbc);
    }

    public static TestHome in(Path directory) throws IOException {
        return openedIn(directory, new FakeWorkerRuntime(), new ManualClock(START)).on(MachineFixture.current());
    }

    public TestHome on(MachineFixture machine) throws IOException {
        return writing("config/machine.yaml", machine.profile());
    }

    public TestHome restarted() throws IOException {
        return openedIn(home.root(), runtime, clock);
    }

    public void restartTheMachine() throws IOException {
        Files.writeString(bootIdFile(home.root()), UUID.randomUUID().toString());
        runtime.loseTheServer();
    }

    private static TestHome openedIn(Path directory, FakeWorkerRuntime runtime, ManualClock clock) throws IOException {
        DataSource store = MigratedStore.openedAt(new EdiHome(directory).store());
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

    public TestHome withTheClaudeLogin(String login, Path configDirectory) throws IOException {
        YAMLMapper yaml = new YAMLMapper();
        ObjectNode profile = (ObjectNode) yaml.readTree(Files.readString(home.machineProfile()));
        ObjectNode claude = profile.get("claude") instanceof ObjectNode existing ? existing : profile.putObject("claude");
        ObjectNode logins = claude.get("logins") instanceof ObjectNode existing ? existing : claude.putObject("logins");
        logins.putObject(login).put("configDirectory", configDirectory.toString());
        return writing("config/machine.yaml", yaml.writeValueAsString(profile));
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
        transaction.executeWithoutResult(_ -> {
            Task task = tasks.get(id);
            tasks.update(new Task(task.id(), task.intake(), TaskState.IN_FLIGHT, task.notes(), task.hold(), task.created(), null));
        });
    }

    public void markDone(TaskId id) {
        transaction.executeWithoutResult(_ -> {
            Task task = tasks.get(id);
            tasks.update(new Task(task.id(), task.intake(), TaskState.DONE, task.notes(), null, task.created(), clock.now()));
        });
    }

    public void inOneTransaction(Runnable work) {
        transaction.executeWithoutResult(_ -> work.run());
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
        return new AddTask(tasks, events, registry, taskKeys(), ticketProposals(), clock, transaction);
    }

    public TaskLinksQuery taskLinks() {
        return new TaskLinksQuery(home, registry, trackers(), dispatches, deliveries, pipelineRuns, boards, decisions, transaction);
    }

    public Trackers trackers() {
        return new Trackers(List.of(jiraAdapter, new EdiNativeTracker(workKeySequences), new GitHubIssuesTracker(), new NoTracker()), machineProfile());
    }

    public TaskKeys taskKeys() {
        return new TaskKeys(registry, trackers());
    }

    public TicketProposals ticketProposals() {
        return new TicketProposals(ticketProposals, tasks, decisions, raiseDecision(), events, registry, trackers(), clock, transaction);
    }

    public KeyTask keyTask() {
        return new KeyTask(tasks, events, taskKeys(), ticketProposals(), captainsWord(), clock, transaction);
    }

    public FakeJira jira() {
        return jira;
    }

    public TestHome trackingWith(Tracker adapter) {
        jiraAdapter = adapter;
        return this;
    }

    public NoteTask noteTask() {
        return new NoteTask(tasks, events, rewriteBrief(), clock, transaction);
    }

    public RewriteBrief rewriteBrief() {
        return new RewriteBrief(tasks, dispatches, workers, registry, gatherBriefFacts(), writtenBriefs(), transaction);
    }

    public GatherBriefFacts gatherBriefFacts() {
        return new GatherBriefFacts(home, machineProfile(), researchBeforeImplementation(), runtime);
    }

    public WrittenBriefs writtenBriefs() {
        return new WrittenBriefs(home, new WorkerBriefs());
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

    public DecisionRoundQuery decisionRounds() {
        return new DecisionRoundQuery(decisions, tasks, boards, events, transaction);
    }

    public CloseDecisionRounds closeDecisionRounds() {
        return new CloseDecisionRounds(decisionRounds(), decisions, events, clock, transaction);
    }

    public AnswerDecision answerDecision() {
        return new AnswerDecision(tasks, decisions, events, ticketProposals(), dependantTasks(), clock, transaction);
    }

    public BacklogQuery backlog() {
        return new BacklogQuery(tasks, transaction);
    }

    public TaskListQuery taskList() {
        return new TaskListQuery(tasks, transaction);
    }

    public TaskDetailQuery taskDetail() {
        return new TaskDetailQuery(tasks, decisions, deliveries, pipelineRuns, taskCosts, workedTimes(), events, clock, transaction);
    }

    public WeekOfWork weekOfWork() {
        return new WeekOfWork(tasks, workedTimes(), registry, machineProfile(), clock, transaction);
    }

    public WorkedTimes workedTimes() {
        return new WorkedTimes(events);
    }

    public DecisionListQuery decisionList() {
        return new DecisionListQuery(decisions, transaction);
    }

    public FleetSnapshotQuery fleetSnapshot() {
        return new FleetSnapshotQuery(tasks, decisions, workers, pipelineRuns, deliveries, inbox, boards, events, clock, transaction);
    }

    public ForkMigrationPlanner forkMigrationPlanner() {
        return new ForkMigrationPlanner(new ForkBacklogReader(json), homeImportPlanner(), clock);
    }

    public HomeImportPlanner homeImportPlanner() {
        return new HomeImportPlanner(home, registry, machineProfile(), new PersonalConfiguration(home), dispatchRulesFile(), new ExampleConfiguration(),
                new ForkRoutingRulesReader(json), new ForkEnvironment(oldPauseResurfaceSeconds), git);
    }

    public MigrateFirstofficer migrateFirstofficer() {
        return new MigrateFirstofficer(home, forkMigrationPlanner(), backlogImport(), new HomeWriter(home));
    }

    public BacklogImport backlogImport() {
        return new BacklogImport(tasks, decisions, workKeySequences, events, clock, transaction);
    }

    public AdoptForkTask adoptForkTask() {
        return new AdoptForkTask(home, forkMigrationPlanner(), backlogImport(), tasks, registry, isolatedCopies(), adoptions, claudeSessions(), events, git,
                processes, clock, transaction);
    }

    public ClaudeSessions claudeSessions() {
        return new ClaudeSessions(new ClaudeLoginFiles(home.root()), machineProfile());
    }

    public Path claudeSessionFolders() {
        return home.root().resolve(".claude").resolve("projects");
    }

    public AdoptionStore adoptions() {
        return adoptions;
    }

    public TestHome rememberingTheOldPauseResurface(String seconds) {
        oldPauseResurfaceSeconds = seconds;
        return this;
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
        return machineProfile;
    }

    public PrepareThePrimary prepareThePrimary(Path checkout) {
        return new PrepareThePrimary(home, new MemoryFiles(home), new PersonalLayer(checkout), clock);
    }

    public StopTheCore stopTheCore() {
        return new StopTheCore(workerPlaces(), stopWorker(), captainsWord());
    }

    public PresenceStore presenceStore() {
        return presence;
    }

    public PresenceQuery presenceQuery() {
        return new PresenceQuery(presence, clock, transaction);
    }

    public ChangePresence changePresence() {
        return new ChangePresence(presence, workerPlaces(), meetingCalendar(), clock, transaction);
    }

    public FollowThePresence followThePresence() {
        return new FollowThePresence(presence, primarySessions, meetingCalendar(), new PersonalConfiguration(home), clock, transaction);
    }

    public ReturnBriefQuery returnBriefs() {
        return new ReturnBriefQuery(fleetSnapshot(), events, tasks, transaction);
    }

    public NotifyTheCaptain notifyTheCaptain() {
        return new NotifyTheCaptain(presence, tasks, windowsToasts(), transaction);
    }

    public DailyMemoryCheck dailyMemoryCheck() {
        return new DailyMemoryCheck(memory(), new MemoryCheckStore(jdbc), windowsToasts(), clock, transaction);
    }

    public synchronized MeetingCalendar meetingCalendar() {
        if (meetingCalendar == null) {
            meetingCalendar = new MeetingCalendar(new OutlookCalendar(programs, machineProfile(), json, windowsSide().executable(),
                    windowsSide().windowsDirectory(), clock), new PersonalConfiguration(home), clock);
        }
        return meetingCalendar;
    }

    public NoticeTheUnreadableCalendar noticeTheUnreadableCalendar() {
        return new NoticeTheUnreadableCalendar(meetingCalendar(), presenceQuery(), windowsToasts());
    }

    public WindowsToasts windowsToasts() {
        return new WindowsToasts(programs, machineProfile(), windowsSide().executable(), windowsSide().windowsDirectory());
    }

    public synchronized WindowsStandIn windowsSide() {
        if (windowsSide == null) {
            try {
                windowsSide = WindowsStandIn.in(home.root().resolve("windows-side"));
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
        }
        return windowsSide;
    }

    public WorkKeySequenceStore workKeySequences() {
        return workKeySequences;
    }

    public HomeSetup homeSetup() {
        return new HomeSetup(home);
    }

    public MemoryQuery memory() {
        return new MemoryQuery(new MemoryFiles(home), machineProfile(), new WorkaroundTickets(tasks, registry, trackers(), transaction), clock);
    }

    public DispatchRulesFile dispatchRulesFile() {
        return new DispatchRulesFile(home, json);
    }

    public IsolatedCopies isolatedCopies() {
        return new IsolatedCopies(home, git, new WorktreeHooks(git), forges());
    }

    public WorkerPlaces workerPlaces() {
        return new WorkerPlaces(workers, tasks, runtime);
    }

    public WorkerTokens workerTokens() {
        return new WorkerTokens(workers, transaction);
    }

    public LaunchWorker launchWorker() {
        return launchWorkerOn(runtime);
    }

    public LaunchWorker launchWorkerOn(WorkerRuntime workerRuntime) {
        return new LaunchWorker(home, tasks, dispatches, workers, adoptions, outages, events, registry, machineProfile(), dispatchRulesFile(),
                isolatedCopies(), new WorkerPlaces(workers, tasks, workerRuntime), new WorkerBriefs(), writtenBriefs(), gatherBriefFacts(),
                resumeNotes(), new Handovers(git, resumeNotes(), pipelineRuns, researchBeforeImplementation(), transaction),
                workerTokens(), workerRuntime, pipeline, processes, machineBoot(), clock, transaction);
    }

    private ResumeNotes resumeNotes() {
        return new ResumeNotes(git, inbox, decisions, transaction);
    }

    public ResearchBeforeImplementation researchBeforeImplementation() {
        return new ResearchBeforeImplementation(home, tasks, dispatches, unplannedShips, events, clock, transaction);
    }

    public DispatchTask dispatchTask() {
        return new DispatchTask(tasks, dispatches, adoptions, events, registry, machineProfile(), dispatchRulesFile(), workerPlaces(), launchWorker(),
                cloneRefreshes(), researchBeforeImplementation(), awayLimit(), captainsWord(), clock, transaction);
    }

    public LaunchDeferredDispatches launchDeferredDispatches() {
        return new LaunchDeferredDispatches(dispatches, tasks, registry, workerPlaces(), awayLimit(), launchWorker(), cloneRefreshes(), events, clock,
                transaction);
    }

    public AwayLimit awayLimit() {
        return new AwayLimit(presence, workerPlaces());
    }

    public WorkerContext workerContext() {
        return new WorkerContext(home, tasks, dispatches, registry);
    }

    public CaptainCheckouts captainCheckouts() {
        return new CaptainCheckouts(git, events, clock, transaction);
    }

    public RecordStatus recordStatus() {
        return new RecordStatus(workers, events, new PersonalConfiguration(home), doneClaims(), deliverAcceptedWork(), followMerges(), clock,
                transaction);
    }

    public Forges forges() {
        return new Forges(List.of(forge, gitLabForge));
    }

    public DeliveryRecords deliveryRecords() {
        return new DeliveryRecords(forges(), deliveries, events, clock);
    }

    public DoneClaims doneClaims() {
        return new DoneClaims(workerContext(), git, inbox, deliveryRecords(), landedWork(), forges(), publishCheck(), events, clock, transaction);
    }

    public DeliverAcceptedWork deliverAcceptedWork() {
        return new DeliverAcceptedWork(workerContext(), deliveryRecords(), followMerges(), captainCheckouts(), cleanUpTask(), transaction);
    }

    public PublishCheck publishCheck() {
        return new PublishCheck(home, git, new GitleaksWhereInstalled(programs, json),
                new PublicationTargets(home, git, registry, machineProfile(), forges()));
    }

    public DeliverDraft deliverDraft() {
        return new DeliverDraft(tasks, workerContext(), publishCheck(), forges(), deliveryRecords(), git, transaction);
    }

    public CloneRefreshes cloneRefreshes() {
        return new CloneRefreshes(cloneRefreshes, registry, git, events, clock, transaction);
    }

    public LandedWorkCheck landedWork() {
        return new LandedWorkCheck(git);
    }

    public CleanUpTask cleanUpTask() {
        return new CleanUpTask(tasks, workers, deliveries, taskCosts, reviews, events, workerContext(), landedWork(),
                new TaskCosts(workers, runtime, pipeline, clock, transaction), pipeline, copyRetirement(), dependantTasks(), clock, transaction);
    }

    public RecountTaskCosts recountTaskCosts() {
        return new RecountTaskCosts(taskCosts, workerContext(), pipeline, events, clock, transaction);
    }

    public CopyRetirement copyRetirement() {
        return new CopyRetirement(home, workers, boards, events, git, runtime, boardListeners(), transaction);
    }

    public DropTask dropTask() {
        return new DropTask(tasks, dispatches, events, workerContext(), landedWork(), copyRetirement(), captainsWord(), dependantTasks(),
                ticketProposals(), clock, transaction);
    }

    public DependantTasks dependantTasks() {
        return new DependantTasks(tasks);
    }

    public DiscardWork discardWork() {
        return new DiscardWork(tasks, events, workerContext(), landedWork(), copyRetirement(), captainsWord(), clock, transaction);
    }

    public DropFromOutage dropFromOutage() {
        return new DropFromOutage(outages, dropTask(), transaction);
    }

    public PromoteScout promoteScout() {
        return new PromoteScout(tasks, addTask(), events, captainsWord(), home, clock, transaction);
    }

    public CaptainWordStore captainWords() {
        return captainWords;
    }

    public CaptainMessageStore captainMessages() {
        return captainMessages;
    }

    public CaptainsWordEvidence captainsWord() {
        return new CaptainsWordEvidence(captainWords, captainMessages, primarySessions, primarySessions(), tasks, decisions, events, clock, transaction);
    }

    public FollowMerges followMerges() {
        return new FollowMerges(tasks, deliveries, events, registry, workerContext(), forges(), deliveryRecords(), cloneRefreshes(), cleanUpTask(),
                clock, transaction);
    }

    public ObservePipelines observePipelines() {
        return new ObservePipelines(workers, pipelineRuns, events, workerContext(), pipeline, followMerges(), clock, transaction);
    }

    public FakeForge forge() {
        return forge;
    }

    public FakeForge forge(ForgeKind kind) {
        return kind == ForgeKind.GITLAB ? gitLabForge : forge;
    }

    public EventFollowers eventFollowers() {
        EmitFleetLedger fleetLedger = new EmitFleetLedger(machineProfile(), new FleetLedger(home, json), tasks, workedTimes(), transaction);
        FollowTickets tickets = new FollowTickets(tasks, registry, trackers(), events, clock, transaction);
        ResearchFirstPilot pilot = new ResearchFirstPilot(unplannedShips, tasks, taskCosts, reviews, dispatches, raiseDecision(), events, clock,
                transaction);
        return new EventFollowers(fleetLedger, tickets, pilot, notifyTheCaptain(), events, eventCursors, transaction);
    }

    public TicketProposalStore ticketProposalStore() {
        return ticketProposals;
    }

    public UnplannedShipStore unplannedShips() {
        return unplannedShips;
    }

    public PipelineReviewStore reviews() {
        return reviews;
    }

    public FakePipeline pipeline() {
        return pipeline;
    }

    public DeliveryStore deliveries() {
        return deliveries;
    }

    public PipelineRunStore pipelineRuns() {
        return pipelineRuns;
    }

    public CloneRefreshStore cloneRefreshStore() {
        return cloneRefreshes;
    }

    public TaskCostStore taskCosts() {
        return taskCosts;
    }

    public WorkerInbox workerInbox() {
        return new WorkerInbox(inbox, workers, events, clock, transaction);
    }

    public SteerWorker steerWorker() {
        return new SteerWorker(tasks, workers, inbox, events, new InboxNotice(runtime), clock, transaction);
    }

    public WorkerHooks workerHooks() {
        return new WorkerHooks(workers, inbox, events, runtime, countRecoveryCost(), stopWorker(), machineProfile(), clock, transaction);
    }

    public GuardToolCall guardToolCall() {
        return new GuardToolCall(home, workerContext(), registry, dispatches, machineProfile(), captainsWord(), runtime, events, clock, transaction);
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

    public PrimaryWaitQuery primaryWaits() {
        return new PrimaryWaitQuery(primarySessions(), wakeStreams(), tasks, transaction);
    }

    public CatchSpark catchSpark() {
        return new CatchSpark(sparks, tasks, events, registry, clock, transaction);
    }

    public SortSpark sortSpark() {
        return new SortSpark(sparks, clock, transaction);
    }

    public PrimaryHooks primaryHooks() {
        return new PrimaryHooks(primarySessions, primarySessions(), captainMessages, changePresence(), presenceQuery(), returnBriefs(), tasks, events,
                processes, wakeStreams(), wakes, runtime, clock, transaction);
    }

    public WorkerListQuery workerList() {
        return new WorkerListQuery(workers, runtime, machineBoot(), machineProfile(), clock, transaction);
    }

    public SessionStartDigestQuery sessionStartDigest() {
        return new SessionStartDigestQuery(primarySessions(), primarySessions, fleetSnapshot(), workerList(), outages, wakes, presenceQuery(), memory(),
                new HomeSetup(home), personalityQuery(), clock, transaction);
    }

    public PersonalityQuery personalityQuery() {
        return new PersonalityQuery(new PersonalConfiguration(home), presenceQuery(), clock);
    }

    public ChangePersonality changePersonality() {
        return new ChangePersonality(new PersonalConfiguration(home), personalityQuery());
    }

    public WaitForWakes waitForWakes() {
        return new WaitForWakes(wakes, wakeStreams(), primarySessions(), decisions, tasks, outages, presence, new PersonalConfiguration(home), clock, transaction);
    }

    public WakeStreams wakeStreams() {
        return new WakeStreams(new WakeStreamStore(jdbc), wakes, processes, clock);
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

    public NoticeFullContexts noticeFullContexts() {
        return new NoticeFullContexts(workerList(), workers, runtime, events, clock, transaction);
    }

    public synchronized NoticeHeavyRunsAtOnce noticeHeavyRunsAtOnce() {
        if (heavyRunsAtOnce == null) {
            heavyRunsAtOnce = new NoticeHeavyRunsAtOnce(workers, processes, events, clock, transaction);
        }
        return heavyRunsAtOnce;
    }

    public NoticeMissedStops noticeMissedStops() {
        return new NoticeMissedStops(workerList(), workers, runtime, steerWorker(), events, clock, transaction);
    }

    public RemindOfUnansweredQuestions remindOfUnansweredQuestions() {
        return new RemindOfUnansweredQuestions(workers, inbox, presence, events, machineProfile(), clock, transaction);
    }

    public OpenWorkerQuestionsQuery openWorkerQuestions() {
        return new OpenWorkerQuestionsQuery(workers, clock, transaction);
    }

    public JudgeWorkers judgeWorkers() {
        return new JudgeWorkers(workerList(), inbox, pipelineRuns, notices, events, outageHoldList(), countRecoveryCost(), machineProfile(), clock,
                transaction);
    }

    public RecoverFromOutage recoverFromOutage() {
        return new RecoverFromOutage(outages, tasks, dispatches, primarySessions(), workerList(),
                new CurrentRoutes(dispatchRulesFile(), machineProfile()), launchWorker(), events, clock, transaction);
    }

    public RelaunchWorker relaunchWorker() {
        return new RelaunchWorker(tasks, workers, workerList(), launchWorker(), transaction);
    }

    public StopWorker stopWorker() {
        return new StopWorker(home, tasks, workers, runtime, processes, events, clock, transaction);
    }

    public ResumeAfterUsageLimits resumeAfterUsageLimits() {
        return new ResumeAfterUsageLimits(workerList(), runtime, notices, events, clock, transaction);
    }

    public NoticeDueHolds noticeDueHolds() {
        return new NoticeDueHolds(tasks, notices, events, clock, transaction);
    }

    public CheckMechanicalChange checkMechanicalChange() {
        return new CheckMechanicalChange(workers, workerContext(), git, new ProjectBuild(programs), runtime, launchWorker(), deliverAcceptedWork(),
                events, clock, transaction);
    }

    public BoardStore boards() {
        return boards;
    }

    public FakeBoardHost boardHost() {
        return boardHost;
    }

    public synchronized BoardListeners boardListeners() {
        if (boardListeners == null) {
            boardListeners = new BoardListeners(boards, boardHost, receiveBoardFeedback(), settleBoardAnswers(), transaction);
        }
        return boardListeners;
    }

    public ArmBoard armBoard() {
        return new ArmBoard(home, tasks, decisions, boards, events, raiseDecision(), boardListeners(), clock, transaction);
    }

    public PostBoardRound postBoardRound() {
        return new PostBoardRound(boards, events, clock, transaction);
    }

    public BoardRoundsQuery boardRounds() {
        return new BoardRoundsQuery(tasks, events, inbox, boards, transaction);
    }

    public ReceiveBoardFeedback receiveBoardFeedback() {
        return new ReceiveBoardFeedback(boards, decisions, inbox, workers, events, new InboxNotice(runtime), settleBoardAnswers(), clock, transaction);
    }

    public SettleBoardAnswers settleBoardAnswers() {
        return new SettleBoardAnswers(boards, decisions, events, inbox, workers, new InboxNotice(runtime), answerDecision(), clock, transaction);
    }

    public ConsoleAccess consoleAccess() {
        return new ConsoleAccess(new ConsoleAccessStore(jdbc), clock, transaction);
    }

    public ConsoleHandOffs consoleHandOffs() {
        return consoleHandOffs;
    }

    public OpenTheSitrepAtSessionStart openTheSitrepAtSessionStart(CaptainsBrowser browser) {
        return new OpenTheSitrepAtSessionStart(machineProfile(), new OpenConsole(consoleAccess(), consoleHandOffs(), browser), primarySessions, clock, transaction);
    }

    public OpenTheConsoleOnRequest openTheConsoleOnRequest(CaptainsBrowser browser) {
        return new OpenTheConsoleOnRequest(new OpenConsole(consoleAccess(), consoleHandOffs(), browser), primarySessions, transaction);
    }

    public AskForARecheck askForARecheck() {
        return new AskForARecheck(tasks, decisions, events, clock, transaction);
    }

    public List<String> eventTypesOf(TaskId task) {
        return jdbc.sql("select type from event where task_id = :task order by sequence").param("task", task.value()).query(String.class).list();
    }
}
