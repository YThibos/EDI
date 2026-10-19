package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
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

import io.github.ythibos.edi.application.migration.MigrationNote;
import io.github.ythibos.edi.application.migration.MigrationPlan;
import io.github.ythibos.edi.application.migration.SetAsideSection;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.PresenceCause;
import io.github.ythibos.edi.domain.presence.PresenceMode;
import io.github.ythibos.edi.domain.primary.BackgroundJob;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.web.migration.MigrationPlanText;
import io.github.ythibos.edi.web.presence.ReturnBriefText;

@DisplayName("Acceptance tests beyond the invariants (12.7 and 17.4)")
class BeyondTheInvariantsTest {

    private static final Path OLD_HOME_FIXTURE = Path.of("src/test/resources/fork-home");

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
        Map<Path, byte[]> before = contentsOf(oldHome);

        MigrationPlan plan = TestHome.in(directory.resolve("edi-home")).forkMigrationPlanner().plan(oldHome);

        assertThat(countsByState(plan.tasks())).containsExactly(
                Map.entry(TaskState.QUEUED, 8L), Map.entry(TaskState.IN_FLIGHT, 3L), Map.entry(TaskState.DONE, 4L));
        assertThat(plan.tasks().stream().collect(Collectors.groupingBy(task -> task.intake().kind(), Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(TaskKind.SHIP, 9L, TaskKind.SCOUT, 1L, TaskKind.CAPTAIN, 5L));
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
        assertThat(plan.highestEdiKey()).isEqualTo(WorkKey.parse("EDI-7"));
        assertThat(plan.setAside()).containsExactly(
                new SetAsideSection("Captain's Call (manual holds, all resolved)", List.of("old-call-one", "old-call-two")));
        assertThat(plan.notes()).extracting(MigrationNote::subject, MigrationNote::note).containsExactly(
                tuple("fog-horn", "appears again on line 30 of the backlog; only its first entry is imported"),
                tuple("gull-survey", "is blocked by bird-census, which is not in the backlog; the dependency is dropped"),
                tuple("loose-end", "names no project; the captain sets one"),
                tuple("relay-mast", "is not imported: its kind secondmate has no counterpart in EDI"),
                tuple("boat-paint", "has a hold of kind external, which is not a hold for the captain; it is not carried"),
                tuple("Gate-Repair", "is not imported: A task id is a kebab-case slug of at most 64 characters, such as fix-login-timeout: Gate-Repair"),
                tuple("D54", "on silent-choice was settled, but no answer was recorded; it is not carried"),
                tuple("Backlog line 32", "is not a task entry and is not imported: This line is not a task entry."));
        assertThat(new MigrationPlanText().text(plan)).contains(
                "Tasks to import (15)", "in-flight  3 (2 held)", "queued     8 (1 held)", "done       4",
                "open      D57", "answered  D55, D56", "the first new decision is D58", "the next is EDI-8", "For the captain (8)");
        assertThat(contentsOf(oldHome)).as("the old home after the dry run").containsExactlyInAnyOrderEntriesOf(before);
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
        home.primaryHooks().sessionStarted(session, null);
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
        String returnBrief = new ReturnBriefText().text(home.primaryHooks().promptSubmitted(session, "I am back.").orElseThrow());

        assertThat(beforeTheThreshold.mode()).isEqualTo(PresenceMode.PRESENT);
        assertThat(afterTheThreshold).extracting(Presence::mode, Presence::cause, Presence::since)
                .containsExactly(PresenceMode.QUIET, PresenceCause.SILENCE, TestHome.START.plus(Duration.ofMinutes(15)));
        assertThat(quietStop).isEqualTo(HookAnswer.PROCEED);
        assertThat(home.windowsSide().toasts()).containsExactly("Decision D1 waits on you | Which lamp?");
        assertThat(afterAMonth.mode()).isEqualTo(PresenceMode.QUIET);
        assertThat(returnBrief).contains("While you were away (quiet since 2026-10-15 09:15Z, after a stretch without a typed message):",
                "Decisions waiting on you (1)", "  D1  Which lamp?");
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

    private static Map<Path, byte[]> contentsOf(Path home) throws IOException {
        try (Stream<Path> files = Files.walk(home)) {
            return files.filter(Files::isRegularFile).collect(Collectors.toMap(Function.identity(), BeyondTheInvariantsTest::bytesOf));
        }
    }

    private static byte[] bytesOf(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private static Map<TaskState, Long> countsByState(List<Task> tasks) {
        return tasks.stream().collect(Collectors.groupingBy(Task::state, () -> new EnumMap<>(TaskState.class), Collectors.counting()));
    }
}
