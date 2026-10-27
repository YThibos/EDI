package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.testkit.engine.EngineTestKit;

import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.migration.AdoptedTask;
import io.github.ythibos.edi.application.migration.ArchiveSurvey;
import io.github.ythibos.edi.application.migration.FileChange;
import io.github.ythibos.edi.application.migration.HomeChange;
import io.github.ythibos.edi.application.migration.HomeFile;
import io.github.ythibos.edi.application.migration.MigrationNote;
import io.github.ythibos.edi.application.migration.MigrationPlan;
import io.github.ythibos.edi.application.migration.MigrationReport;
import io.github.ythibos.edi.application.migration.SetAsideSection;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.PresenceCause;
import io.github.ythibos.edi.domain.presence.PresenceMode;
import io.github.ythibos.edi.domain.primary.BackgroundJob;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.ExampleHome;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.PresenterFixtures;
import io.github.ythibos.edi.web.migration.MigrationReportText;

@DisplayName("Acceptance tests beyond the invariants (12.7 and 17.4)")
class BeyondTheInvariantsTest {

    private static final Path OLD_HOME_FIXTURE = Path.of("src/test/resources/fork-home");
    private static final String LIGHTHOUSE_REGISTRY = """
            projects:
              - name: lighthouse
                tracker: { kind: edi, projectKey: LHT }
                forge: { kind: github, path: example-owner/lighthouse, account: example-owner }
                base: main
            """;
    private static final Pattern OUTSIDE_A_CLAUDE_FOLDER_NAME = Pattern.compile("[^A-Za-z0-9]");
    private static final String ABSENT = "absent";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    @Test
    @Tag("adapter")
    @DisplayName("AT-40 a deliberately leaked child process in a test fails the build")
    void aLeakedChildProcessFailsTheBuild() {
        EngineTestKit.engine("junit-jupiter").selectors(selectClass(LeakingTest.class)).execute().testEvents()
                .assertThatEvents().haveExactly(1, event(test("leavesItsChildRunning"),
                        finishedWithFailure(instanceOf(AssertionError.class), message(text -> text.contains("running; a test stops every process it starts")))));

        assertThat(LeakingTest.leaked).isNotEmpty().noneMatch(ProcessHandle::isAlive);
    }

    @Test
    @Tag("adapter")
    @DisplayName("AT-41 the migration dry run on a copy of the old home reproduces its counts and reports every unmapped item")
    void theMigrationDryRunReproducesTheCounts() throws IOException {
        Path oldHome = copyOf(OLD_HOME_FIXTURE);
        Map<Path, String> before = entriesOf(oldHome);

        MigrationReport report = TestHome.in(directory.resolve("edi-home")).migrateFirstofficer().dryRun(oldHome);
        MigrationPlan plan = report.plan();

        assertThat(countsByState(plan.tasks())).containsExactly(
                Map.entry(TaskState.QUEUED, 10L), Map.entry(TaskState.IN_FLIGHT, 3L), Map.entry(TaskState.DONE, 4L));
        assertThat(plan.tasks().stream().collect(Collectors.groupingBy(task -> task.intake().kind(), Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(TaskKind.SHIP, 10L, TaskKind.SCOUT, 2L, TaskKind.CAPTAIN, 5L));
        assertThat(plan.tasks().stream().filter(Task::isHeld))
                .extracting(task -> task.id().value(), task -> task.hold().kind(), task -> task.hold().until())
                .containsExactly(
                        tuple("tide-tables", HoldKind.DEFERRAL, HoldUntil.captainsWord()),
                        tuple("beacon-colour", HoldKind.DECISION, HoldUntil.captainsWord()),
                        tuple("visit-day", HoldKind.DEFERRAL, HoldUntil.day(LocalDate.of(2026, 11, 1))));
        assertThat(plan.decisions().stream().filter(Decision::isOpen)).extracting(Decision::number).containsExactly(new DecisionNumber(57));
        assertThat(plan.decisions().stream().filter(decision -> !decision.isOpen()))
                .extracting(Decision::number, decision -> decision.answer().option(), decision -> decision.answer().channel())
                .containsExactly(
                        tuple(new DecisionNumber(55), new OptionKey('A'), AnswerChannel.CHAT),
                        tuple(new DecisionNumber(56), null, AnswerChannel.BOARD));
        assertThat(plan.decisionSequence().highest()).isEqualTo(57);
        assertThat(plan.decisionSequence().firstNewNumber()).isEqualTo(new DecisionNumber(58));
        assertThat(plan.highestEdiKey()).as("a key named only in a task's notes").isEqualTo(WorkKey.parse("EDI-9"));
        assertThat(plan.setAside()).containsExactly(
                new SetAsideSection("Captain's Call (manual holds, all resolved)", List.of("old-call-one", "old-call-two")));
        assertThat(plan.oldHarnessWork()).containsExactly("old-harness-polish");
        assertThat(plan.notes().subList(0, 10)).extracting(MigrationNote::subject, MigrationNote::note).containsExactly(
                tuple("fog-horn", "appears again on line 31 of the backlog; only its first entry is imported"),
                tuple("gull-survey", "is blocked by bird-census, which is not in the backlog; the dependency is dropped"),
                tuple("loose-end", "names no project; the captain sets one"),
                tuple("relay-mast", "is not imported: its kind secondmate has no counterpart of mine"),
                tuple("boat-paint", "waits on something outside me (\"waiting on supplies\"); I hold a task only for the captain, so the wait is not"
                        + " carried"),
                tuple("Gate-Repair", "is not imported: I name a task in at most 64 lower-case letters, digits and dashes, such as fix-login-timeout"),
                tuple("night-watch", "waits until 2026-11-15 on something outside me (\"waits for a stormy night to test the lamp\"); I hold a task"
                        + " only for the captain, so the wait is not carried"),
                tuple("polish-follow-up", "is blocked by old-harness-polish, which is not imported; the dependency is dropped"),
                tuple("D54", "on silent-choice was settled, but no answer was recorded; it is not carried"),
                tuple("Backlog line 36", "is not a task entry and is not imported: This line is not a task entry."));
        assertThat(new MigrationReportText().text(report)).contains(
                "Dry run of the import from " + oldHome + "; nothing was changed.", "Tasks to import (17)", "in-flight  3 (2 held)",
                "queued     10 (1 held)", "done       4", "open      D57", "answered  D55, D56", "the first new decision is D58", "the next is EDI-10",
                "Set aside, as they improve the old harness I replace (1)", "For the captain (" + plan.notes().size() + ")")
                .doesNotContain("kind null", "of kind");
        assertThat(changesTo(oldHome, before)).as("the old home after the dry run").isEmpty();
    }

    @Test
    @Tag("adapter")
    @DisplayName("AT-47 the import writes what its dry run planned into a fresh home, leaves the old home as it was and runs again as a no-op")
    void theImportWritesWhatItsDryRunPlanned() throws IOException {
        Path oldHome = withTheOldClones(copyOf(OLD_HOME_FIXTURE));
        Map<Path, String> before = entriesOf(oldHome);
        TestHome home = freshHomeIn(directory.resolve("edi-home"));

        MigrationReport planned = home.migrateFirstofficer().dryRun(oldHome);
        MigrationReport imported = home.migrateFirstofficer().run(oldHome, false, Actor.PRIMARY);
        long eventsOfTheImport = home.events().latestSequence();
        MigrationReport again = home.migrateFirstofficer().run(oldHome, false, Actor.PRIMARY);

        assertThat(imported.backlog()).isEqualTo(planned.backlog());
        assertThat(imported.home()).isEqualTo(planned.home());
        assertThat(home.tasks().ids()).containsExactlyElementsOf(planned.plan().tasks().stream().map(Task::id).toList());
        planned.plan().tasks().forEach(task -> assertThat(home.tasks().get(task.id())).isEqualTo(task));
        assertThat(home.decisions().all()).containsExactlyInAnyOrderElementsOf(planned.plan().decisions());
        assertThat(home.decisions().lastNumberIssued()).isEqualTo(57);
        assertThat(home.workKeySequences().upcoming(new ProjectKey("EDI"))).isEqualTo(WorkKey.parse("EDI-10"));
        for (HomeFile file : planned.plan().home().files()) {
            assertThat(home.home().root().resolve(file.path())).as(file.path()).hasContent(file.content());
        }
        assertThat(home.home().root().resolve("data/archive/firstofficer/backlog.md")).hasSameBinaryContentAs(oldHome.resolve("data/backlog.md"));
        assertThat(Files.getPosixFilePermissions(home.home().root().resolve("data/archive/firstofficer/tasks/tide-tables/screens/chart.png")))
                .as("an archived file").containsExactly(PosixFilePermission.OWNER_READ);
        assertThat(home.home().root().resolve("data/archive/firstofficer/state/held/tide-tables.md")).isRegularFile();
        assertThat(home.home().root().resolve("data/archive/firstofficer/config/crew-dispatch.json")).isRegularFile();
        assertThat(home.home().root().resolve("data/tasks/lantern-rework/firstofficer/brief.md")).hasContent("Rework the lantern controller.");
        assertThat(home.home().root().resolve("data/tasks/flag-pole")).as("a done task's folder stays in the archive only").doesNotExist();
        assertThat(home.registry().entryOf(new ProjectName("lighthouse")))
                .extracting(entry -> entry.forge(), entry -> entry.delivery(), entry -> entry.craftRules())
                .containsExactly(null, DeliveryMode.DRAFT_REVIEW, true);
        assertThat(home.registry().entryOf(new ProjectName("harbour")).delivery()).isEqualTo(DeliveryMode.LOCAL_BRANCH);
        assertThat(home.registry().entryOf(new ProjectName("EDI")).project().workKeys()).isEqualTo(new ProjectKey("EDI"));
        assertThat(home.dispatchRulesFile().validatedAgainst(home.machineProfile().workers().models()).categories()).hasSize(8);
        assertThat(home.homeSetup().missing()).as("the session-start digest lists nothing missing").isEmpty();
        assertThat(home.memory().check().passes()).isTrue();
        assertThat(home.home().root().resolve("memory/machine.md")).content().contains("## Credentials and access", "## Release notes in harbour");
        assertThat(home.home().root().resolve("memory/archive.md")).content().contains("## 2026-09-16 stow", "## firstofficer charts its own course");
        assertThat(home.home().root().resolve("memory/profile.md")).content().contains("Render the report with `edi report`").doesNotContain("fm-captain-report");
        assertThat(home.home().root().resolve("memory/learnings.md")).content().contains("The lamp needs oil").doesNotContain("Monitoring restarts itself").doesNotContain("fleet lock");

        assertThat(again.backlog().alreadyInEdi()).hasSameSizeAs(planned.plan().tasks());
        assertThat(again.home().files()).extracting(FileChange::change).containsOnly(HomeChange.UNCHANGED);
        assertThat(again.home().links()).extracting(FileChange::change).containsOnly(HomeChange.UNCHANGED);
        assertThat(again.home().archive()).extracting(ArchiveSurvey::added, ArchiveSurvey::replaced).containsExactly(0, 0);
        assertThat(home.events().latestSequence()).as("events after running again").isEqualTo(eventsOfTheImport);
        assertThat(changesTo(oldHome, before)).as("the old home after the import").isEmpty();
    }

    @Test
    @Tag("adapter")
    @DisplayName("AT-47 the import refuses an EDI home that holds work of its own unless told to overwrite, and then keeps that work and each replaced file")
    void theImportOverwritesAHomeInUseOnlyWhenTold() throws IOException {
        Path oldHome = copyOf(OLD_HOME_FIXTURE);
        TestHome home = TestHome.in(directory.resolve("edi-home")).registering(LIGHTHOUSE_REGISTRY)
                .storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "lighthouse", "LHT-1")));
        String machineProfile = Files.readString(home.home().machineProfile());

        assertThatThrownBy(() -> home.migrateFirstofficer().run(oldHome, false, Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("already holds 1 task and 0 decisions of its own and 2 files the import would replace")
                .hasMessageContaining("--overwrite");
        assertThat(home.tasks().ids()).containsExactly(new TaskId("fit-lamp"));
        assertThat(home.home().root().resolve("data/archive")).doesNotExist();

        home.migrateFirstofficer().run(oldHome, true, Actor.PRIMARY);

        assertThat(home.tasks().get(new TaskId("fit-lamp")).intake().workKey()).isEqualTo(WorkKey.parse("LHT-1"));
        assertThat(home.tasks().ids()).hasSize(18);
        assertThat(home.home().root().resolve("config/machine.yaml.before-migration")).hasContent(machineProfile);
        assertThat(home.home().root().resolve("config/projects.yaml.before-migration")).hasContent(LIGHTHOUSE_REGISTRY);
        assertThat(home.registry().entryOf(new ProjectName("lighthouse")).project().workKeys()).isEqualTo(new ProjectKey("LHT"));
        assertThat(home.registry().projects()).extracting(project -> project.name().value()).containsExactly("lighthouse", "harbour", "EDI");
    }

    @Test
    @Tag("adapter")
    @DisplayName("AT-48 adopting an open task registers its copy in place while a process holds it, then moves it under EDI's worktrees with its"
            + " uncommitted work, and its next dispatch resumes the recorded session there")
    void anAdoptedTaskResumesItsSessionInItsOwnCopy() throws IOException {
        Path oldHome = copyOf(OLD_HOME_FIXTURE);
        TestHome home = TestHome.in(directory.resolve("edi-home")).registering(LIGHTHOUSE_REGISTRY)
                .writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json"));
        FixtureRepository upstream = FixtureRepository.initialise(Files.createDirectories(directory.resolve("upstream/lighthouse")))
                .write("README.md", "lamp\n").commit("Start");
        upstream.clonedInto(home.home().root().resolve("projects/lighthouse"));
        Path oldCopy = oldCopyOn(upstream, oldHome, "feature/LHT-3");
        String oldHead = FixtureRepository.at(oldCopy).write("lantern.txt", "brighter\n").commit("LHT-3 Rework the lantern").head();
        Files.writeString(oldCopy.resolve("lantern.txt"), "brightest\n");
        Files.writeString(oldCopy.resolve("draft.txt"), "unsaved\n");
        UUID session = UUID.randomUUID();
        Path transcript = Files.createDirectories(home.claudeSessionFolders().resolve(claudeFolderOf(oldCopy))).resolve(session + ".jsonl");
        Files.writeString(transcript, "{\"type\":\"user\"}\n");
        TaskId lantern = new TaskId("lantern-rework");
        BranchName branch = BranchName.parse("feature/LHT-3");
        Path copy = home.home().worktreeOf(new ProjectName("lighthouse"), lantern);

        assertThatThrownBy(() -> home.adoptForkTask().adopt(lantern, oldHome, BranchName.parse("feature/LHT-4"), oldCopy, session, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("has feature/LHT-3 checked out, not feature/LHT-4");
        assertThat(home.tasks().ids()).isEmpty();

        Process holder = processes.register(new ProcessBuilder("sleep", "30").directory(oldCopy.toFile()).start());
        AdoptedTask inPlace = home.adoptForkTask().adopt(lantern, null, branch, oldCopy, session, Actor.PRIMARY);

        assertThat(inPlace.moved()).isFalse();
        assertThat(Files.readSymbolicLink(copy)).isEqualTo(oldCopy);
        assertThat(oldCopy.resolve("draft.txt")).hasContent("unsaved");
        holder.destroy();
        assertThat(holder.onExit().join().isAlive()).isFalse();

        AdoptedTask adopted = home.adoptForkTask().adopt(lantern, oldHome, branch, oldCopy, session, Actor.PRIMARY);
        home.adoptForkTask().adopt(lantern, oldHome, branch, oldCopy, session, Actor.PRIMARY);

        assertThat(adopted).extracting(AdoptedTask::moved, AdoptedTask::copy).containsExactly(true, copy);
        assertThat(oldCopy).doesNotExist();
        assertThat(Files.isSymbolicLink(copy)).isFalse();
        assertThat(FixtureRepository.at(copy).head()).isEqualTo(oldHead);
        assertThat(copy.resolve("lantern.txt")).hasContent("brightest");
        assertThat(copy.resolve("draft.txt")).hasContent("unsaved");
        assertThat(home.git().uncommittedPaths(copy)).containsExactlyInAnyOrder("lantern.txt", "draft.txt");
        assertThat(home.git().currentBranch(copy)).isEqualTo("feature/LHT-3");
        assertThat(home.git().commonDirectory(copy).toRealPath()).isEqualTo(home.home().root().resolve("projects/lighthouse/.git").toRealPath());
        assertThat(home.tasks().get(lantern)).extracting(Task::state, task -> task.intake().workKey())
                .containsExactly(TaskState.IN_FLIGHT, WorkKey.parse("LHT-3"));
        assertThat(home.claudeSessionFolders().resolve(claudeFolderOf(copy)).resolve(session + ".jsonl")).hasSameTextualContentAs(transcript);

        home.dispatchTask().dispatch(new TaskReference.ById(lantern), new DispatchRequest(CategoryId.IMPLEMENTATION, "Carry on.", List.of(), null,
                false), CallingSession.UNNAMED, Actor.PRIMARY);

        Launch launch = home.runtime().launches().getLast();
        assertThat(launch).extracting(Launch::kind, Launch::session, Launch::worktree).containsExactly(LaunchKind.RESUME, session, copy);
        assertThat(home.events().timelineOf(lantern)).extracting(RecordedEvent::type)
                .containsExactly("TaskImported", "TaskAdopted", "TaskDispatched");
    }

    @TestFactory
    @Tag("adapter")
    @DisplayName("AT-42 the report presenter matches the old renderer on every ported golden case and the 2 MB backlog case")
    Stream<DynamicTest> theReportMatchesTheGoldenCases() {
        return ReportGoldenCases.all().stream()
                .map(golden -> DynamicTest.dynamicTest(golden.name(), () -> assertThat(golden.rendered(directory)).isEqualTo(golden.golden())));
    }

    @Test
    @Tag("adapter")
    @DisplayName("AT-46 presence switches to quiet without input, stays silent on routine wakes, never turns silence into away, and demo holds present")
    void presenceFollowsTheCaptain() throws IOException {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY)
                .writing("config/machine.yaml", "notify:\n  windowsToast: true\npresence:\n  calendar: outlook\n")
                .writing("config/personal.yaml", "presence:\n  calendarIgnore: [Example at the office]\n");
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(session, "Fit the brass lamp.");
        home.addTask().add(Intakes.ship("fit-lamp", "Fit the brass lamp"), Actor.PRIMARY);

        Presence beforeTheThreshold = followedAfter(home, Duration.ofMinutes(14));
        Presence afterTheThreshold = followedAfter(home, Duration.ofMinutes(1));
        home.primaryHooks().promptSubmitted(session, "<task-notification><status>completed</status></task-notification>");
        home.raiseDecision().raise(TaskReference.parse("fit-lamp"), Cards.card("Which lamp?"), Actor.PRIMARY);
        HookAnswer quietStop = home.primaryHooks().stopping(session, List.of(new BackgroundJob("shell", "running", "edi wait")));
        home.eventFollowers().followTheLog();
        home.eventFollowers().followTheLog();
        Presence afterAMonth = followedAfter(home, Duration.ofDays(30));
        String returnBrief = PresenterFixtures.returnBriefText().text(home.primaryHooks().promptSubmitted(session, "I am back.").orElseThrow());

        assertThat(beforeTheThreshold.mode()).isEqualTo(PresenceMode.PRESENT);
        assertThat(afterTheThreshold).extracting(Presence::mode, Presence::cause, Presence::since)
                .containsExactly(PresenceMode.QUIET, PresenceCause.SILENCE, TestHome.START.plus(Duration.ofMinutes(15)));
        assertThat(quietStop).isEqualTo(HookAnswer.PROCEED);
        assertThat(home.windowsSide().toasts()).containsExactly("Decision D1 waits on you | Which lamp?");
        assertThat(afterAMonth.mode()).isEqualTo(PresenceMode.QUIET);
        assertThat(returnBrief).contains("While you were away (quiet since 2026-10-15 09:15 UTC, after a stretch without a typed message):",
                "Needs your decision (1)", "  D1  Which lamp?");
        assertThat(home.presenceQuery().current().mode()).isEqualTo(PresenceMode.PRESENT);

        Instant meetingStart = home.clock().now().plus(Duration.ofMinutes(5));
        home.windowsSide().showingTheCalendar(calendarOf(
                calendarEntry("Example at the office", meetingStart.minus(Duration.ofHours(2)), meetingStart.plus(Duration.ofHours(1)), false, 3, 2),
                calendarEntry("Holiday", meetingStart.minus(Duration.ofHours(10)), meetingStart.plus(Duration.ofHours(14)), true, 0, 2),
                calendarEntry("Sprint review", meetingStart, meetingStart.plus(Duration.ofHours(1)), false, 3, 2)));
        Presence underPresenceMarkers = followedAfter(home, Duration.ofMinutes(4));
        Presence inTheMeeting = followedAfter(home, Duration.ofMinutes(2));
        home.primaryHooks().promptSubmitted(session, "/demo");
        Presence demo = home.changePresence().demo();
        Presence demoThroughTheMeeting = followedAfter(home, Duration.ofMinutes(50));
        Presence afterTheMeeting = followedAfter(home, Duration.ofMinutes(10));

        assertThat(underPresenceMarkers.mode()).isEqualTo(PresenceMode.PRESENT);
        assertThat(inTheMeeting).extracting(Presence::mode, Presence::cause).containsExactly(PresenceMode.QUIET, PresenceCause.MEETING);
        assertThat(demo.demoUntil()).isEqualTo(meetingStart.plus(Duration.ofHours(1)));
        assertThat(demoThroughTheMeeting.mode()).isEqualTo(PresenceMode.PRESENT);
        assertThat(demoThroughTheMeeting.demoUntil()).isEqualTo(demo.demoUntil());
        assertThat(afterTheMeeting).extracting(Presence::mode, Presence::cause).containsExactly(PresenceMode.QUIET, PresenceCause.SILENCE);
    }

    private static Presence followedAfter(TestHome home, Duration pause) {
        home.clock().advance(pause);
        home.followThePresence().follow();
        return home.presenceQuery().current();
    }

    private static String calendarOf(String... entries) {
        return "[" + String.join(",", entries) + "]";
    }

    private static String calendarEntry(String subject, Instant start, Instant end, boolean allDay, int response, int showing) {
        return "{\"Subject\":\"" + subject + "\",\"Start\":\"" + start + "\",\"End\":\"" + end + "\",\"AllDay\":" + allDay
                + ",\"ResponseStatus\":" + response + ",\"BusyStatus\":" + showing + "}";
    }

    static final class LeakingTest {

        private static final List<ProcessHandle> leaked = new CopyOnWriteArrayList<>();

        @RegisterExtension
        private final TestProcesses processes = new TestProcesses();

        @Test
        void leavesItsChildRunning() throws IOException {
            Process child = processes.register(new ProcessBuilder("sh", "-c", "sleep 60 & echo started; wait").start());
            child.inputReader().readLine();
            leaked.add(child.toHandle());
            child.descendants().forEach(leaked::add);
        }
    }

    private Path withTheOldClones(Path oldHome) throws IOException {
        FixtureRepository.initialise(Files.createDirectories(oldHome.resolve("projects/lighthouse"))).write("README.md", "lamp\n").commit("Start")
                .run("remote", "add", "origin", "git@forge.example.org:team/lighthouse.git");
        FixtureRepository.initialise(Files.createDirectories(oldHome.resolve("projects/harbour"))).write("README.md", "quay\n").commit("Start");
        return oldHome;
    }

    private static TestHome freshHomeIn(Path directory) throws IOException {
        TestHome home = TestHome.in(directory);
        Files.delete(home.home().machineProfile());
        return home;
    }

    private Path oldCopyOn(FixtureRepository upstream, Path oldHome, String branch) throws IOException {
        Path copy = directory.resolve("pool/1/lighthouse");
        upstream.clonedInto(oldHome.resolve("projects/lighthouse")).run("worktree", "add", "--quiet", "-b", branch, copy.toString());
        return copy;
    }

    private static String claudeFolderOf(Path workingDirectory) {
        return OUTSIDE_A_CLAUDE_FOLDER_NAME.matcher(workingDirectory.toAbsolutePath().normalize().toString()).replaceAll("-");
    }

    private Path copyOf(Path fixture) throws IOException {
        Path copy = directory.resolve("old-home");
        try (Stream<Path> files = Files.walk(fixture)) {
            for (Path file : files.toList()) {
                Path target = copy.resolve(fixture.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        return copy;
    }

    private static Map<Path, String> entriesOf(Path home) throws IOException {
        try (Stream<Path> entries = Files.walk(home)) {
            return entries.collect(Collectors.toMap(entry -> nameWithin(home, entry), BeyondTheInvariantsTest::descriptionOf));
        }
    }

    private static Path nameWithin(Path home, Path entry) {
        return home.getFileName().resolve(home.relativize(entry));
    }

    private static String descriptionOf(Path entry) {
        try {
            PosixFileAttributes attributes = Files.readAttributes(entry, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return contentOf(entry, attributes) + ", " + PosixFilePermissions.toString(attributes.permissions()) + ", modified "
                    + attributes.lastModifiedTime();
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private static String contentOf(Path entry, PosixFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink()) {
            return "link to " + Files.readSymbolicLink(entry);
        }
        return attributes.isDirectory() ? "folder" : "file with SHA-256 " + sha256Of(Files.readAllBytes(entry));
    }

    private static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static List<String> changesTo(Path home, Map<Path, String> before) throws IOException {
        Map<Path, String> after = entriesOf(home);
        return Stream.concat(before.keySet().stream(), after.keySet().stream())
                .distinct()
                .sorted()
                .filter(entry -> !Objects.equals(before.get(entry), after.get(entry)))
                .map(entry -> changeOf(entry, before.get(entry), after.get(entry)))
                .toList();
    }

    private static String changeOf(Path entry, String before, String after) {
        return entry + ": " + Objects.requireNonNullElse(before, ABSENT) + " -> " + Objects.requireNonNullElse(after, ABSENT);
    }

    private static Map<TaskState, Long> countsByState(List<Task> tasks) {
        return tasks.stream().collect(Collectors.groupingBy(Task::state, () -> new EnumMap<>(TaskState.class), Collectors.counting()));
    }
}
