package io.github.ythibos.edi.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.StandInGitHub;
import io.github.ythibos.edi.testing.StandInGitHub.Run;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class GardeningSkillTest {

    private static final Path SCRIPT = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize()
            .resolve("claude/skills/gardening/gardening.mjs");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String WEEK = "2026-10-12";
    private static final String SESSION = "5e55a0e1-0000-4000-8000-000000000001";
    private static final String LAMPS_RECORD = String.join("/", "docs", "decisions", "lamps.md");
    private static final String CATALOGUE = """
            # Rule catalogue

            | Rule | Where it lives | What it protects |
            |---|---|---|
            | `guard.forced-push` | `GitPushes` | History on a remote is never rewritten by a forced push. |
            | `authority.captains-word` | `CaptainsWordEvidence` | Destructive and irreversible acts wait for the captain's word. |
            | `guard.harbour-lights` | `HarbourLights` | The harbour lights stay on at night. |
            """;
    private static final String WORLD = """
            {"owner": {"login": "harbour-master", "type": "User"},
             "issues": [
               {"number": 5, "id": 1005, "title": "EDI-5 An old idea", "body": "", "state": "open", "labels": [], "updated_at": "2026-09-01T08:00:00Z"},
               {"number": 6, "id": 1006, "title": "EDI-6 A fresh idea", "body": "", "state": "open", "labels": [], "updated_at": "2026-10-18T08:00:00Z"},
               {"number": 7, "id": 1007, "title": "EDI-7 A finished thing", "body": "", "state": "closed", "labels": [], "updated_at": "2026-10-02T08:00:00Z"}
             ]}""";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private TestHome home;
    private Path transcripts;
    private Path round;
    private int lines;

    @BeforeEach
    void inventAHome() throws IOException {
        assumeTrue(InstalledPrograms.answers("node", "-e", "require('node:sqlite')"), "node with node:sqlite is not installed");
        assumeTrue(InstalledPrograms.answers("git", "--version"), "git is not installed");
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("tune-horn", "harness", "HRN-4"),
                WorkerFixtures.queuedShip("paint-hull", "harness", "HRN-5"), WorkerFixtures.queuedShip("light-beacon", "harness", "HRN-6"),
                WorkerFixtures.queuedShip("sink-buoy", "harness", "HRN-8")));
        transcripts = Files.createDirectories(directory.resolve("transcripts"));
        round = directory.resolve("round");
    }

    @Test
    void countsTheRoutinesAndTheOutcomesOfTheWeekAgainstThePrevious() throws Exception {
        inventTheWeek();

        JsonNode gathered = gather();

        assertThat(gathered.at("/week").toString()).isEqualTo("{\"monday\":\"2026-10-12\",\"from\":\"2026-10-12 00:00\",\"until\":\"2026-10-20 00:00\"}");
        assertThat(gathered.at("/outcomes/week").toString()).isEqualTo("{\"captainsCorrections\":1,\"captainsDisagreements\":1,\"captainsQuestions\":1,"
                + "\"questionToAnswerMinutes\":25,\"reviewRoundsPerRequest\":3.5,\"doneToLandedMinutes\":90,\"defectsAfterMerge\":1}");
        assertThat(gathered.at("/outcomes/previous").toString()).isEqualTo("{\"captainsCorrections\":1,\"captainsDisagreements\":0,\"captainsQuestions\":0,"
                + "\"questionToAnswerMinutes\":4,\"reviewRoundsPerRequest\":null,\"doneToLandedMinutes\":null,\"defectsAfterMerge\":0}");
        assertThat(gathered.at("/routines/week/routines/turn report rendering").asInt()).isEqualTo(2);
        assertThat(gathered.at("/routines/week/routines/worker inspection (list, peek, task show)").asInt()).isEqualTo(10);
        assertThat(gathered.at("/routines/previous/routines/turn report rendering").asInt()).isEqualTo(1);
        assertThat(gathered.at("/routines/week/signals/stop-hook blocks").asInt()).isEqualTo(1);
        assertThat(gathered.at("/routines/week/toolCalls").asInt()).isEqualTo(12);
    }

    @Test
    void findsEverySlipOfTheWeekRankedByWeightWithItsEvidence() throws Exception {
        inventTheWeek();

        JsonNode slips = gather().get("slips");

        assertThat(texts(slips)).containsExactly(
                "captain-disagrees: the captain wrote: \"(!) \"The lamp is lit\" - it is not.\"",
                "merged-against-a-warning: tune-horn was merged while it was changing, 90 minutes after its worker worked again past its done",
                "defect-after-merge: fix-hull fixes HRN-5 after paint-hull merged: \"Fix the hull's paint\"",
                "captain-corrects: the captain wrote: \"Why is the horn still out of tune?\"",
                "primary-admits: the primary wrote: \"That was my mistake: I missed the horn's question.\"",
                "question-left-open: the question socket of fit-lamp#1 waited 25 minutes for its resolving steer, past the 10-minute threshold",
                "gate-parked: the gate nm-01RUN-review of tune-horn#1 waited 45 minutes for its resolving steer, past the 30-minute threshold",
                "request-changing: tune-horn was changing for 90 minutes after its done, past the 60-minute threshold",
                "steer-unacknowledged: steer 2 to tune-horn was never acknowledged",
                "review-rounds: tune-horn took 5 review rounds before its merge, at or past the threshold of 5",
                "captain-questions: the captain wrote: \"(?) \"I parked the hull\" - why?\"",
                "question-left-open: the question lens of light-beacon#2 waited 18 minutes until its worker moved on without an answer, past the 10-minute threshold",
                "decision-overridden: decision D3 was answered with a note that none of its options carries: \"Neither: light it by hand.\"",
                "captains-word-refused: authority.captains-word refused 1 time",
                "safety-check-refused: guard.forced-push refused 10 times");
        assertThat(gather().at("/top").toString()).isEqualTo("[1,2,3]");
        assertThat(slips.get(1).get("evidence").toString()).contains("2026-10-14 10:00 tune-horn#1 reports working: Tuning it again.");
        assertThat(slips.get(4).get("evidence").toString()).contains("2026-10-14 12:00 the captain had written: Why is the horn still out of tune?");
        assertThat(slips.get(13).get("evidence").toString()).isEqualTo("[\"2026-10-17 10:00 worker was refused: a reason\"]");
    }

    @Test
    void readsTheRuleTelemetryForRulesFiredOftenAndRulesThatNeverFired() throws Exception {
        inventTheWeek();

        JsonNode rules = gather().get("rules");

        assertThat(rules.get("recordingSince").asString()).isEqualTo("2026-10-07T09:00:00Z");
        assertThat(rules.get("fired").toString()).isEqualTo("[{\"rule\":\"guard.forced-push\",\"protects\":\"History on a remote is never rewritten by a forced"
                + " push.\",\"refused\":10,\"warned\":0,\"allowed\":0,\"total\":10,\"previous\":1,\"last\":\"2026-10-17T09:09:00Z\"},"
                + "{\"rule\":\"authority.captains-word\",\"protects\":\"Destructive and irreversible acts wait for the captain's word.\",\"refused\":1,"
                + "\"warned\":0,\"allowed\":0,\"total\":1,\"previous\":0,\"last\":\"2026-10-17T10:00:00Z\"}]");
        assertThat(rules.get("often").toString()).isEqualTo("[\"guard.forced-push\"]");
        assertThat(rules.get("neverFired").toString()).isEqualTo("[{\"rule\":\"guard.harbour-lights\",\"protects\":\"The harbour lights stay on at night.\"}]");
    }

    @Test
    void findsTheCommandsChainedByHandFiveTimesOrMore() throws Exception {
        inventTheWeek();

        assertThat(gather().get("handSequences").toString()).isEqualTo("[{\"sequence\":[\"edi worker list\",\"edi task show\"],\"count\":5}]");
    }

    @Test
    void findsTheHygieneDriftAndTellsWhatAStandingRuleCovers() throws Exception {
        inventTheWeek();

        JsonNode hygiene = gather().get("hygiene");

        assertThat(StreamSupport.stream(hygiene.spliterator(), false).map(found -> (found.get("standing").asBoolean() ? "standing " : "word ")
                + found.get("kind").asString() + ": " + found.get("text").asString())).containsExactly(
                        "word long-hold: light-beacon has been held since 2026-09-20 with no date and nothing named that lifts the hold: \"Waits for a calmer sea\"",
                        "standing orphaned-worktree: paint-hull is merged and its isolated copy is still there: edi cleanup paint-hull retires it",
                        "word orphaned-worktree: the isolated copy harness/sink-buoy belongs to a dropped task",
                        "word orphaned-branch: the branch feature/EDI-9 of edi belongs to no open task",
                        "word untouched-backlog-item: EDI-5 An old idea has not changed since 2026-09-01: https://github.com/example/edi/issues/5",
                        "word story-file-disagrees: docs/jira/HRN-3.md of edi says HRN-3 is \"To Do\" where the tracker last moved it to \"Test in DEV\"",
                        "word todo-on-a-closed-ticket: src/Lamp.java:2 of edi carries a TODO or FIXME for EDI-7, which is closed",
                        "word decision-names-removed-code: " + LAMPS_RECORD + " of edi names RemovedKeeper, which the code once carried and no longer does",
                        "standing memory-budget: the startup memory holds 17000 of its 18000 tokens, past 90% of its budget: /stow curates it");
    }

    @Test
    void putsTheTopSlipsTheirDraftsTheRulesTheHygieneAndTheSparksOnOneBoard() throws Exception {
        inventTheWeek();
        gather();

        Run chosen = script("choose", round.toString(), "1", "6", "2");
        Run board = script("board", round.toString());

        assertThat(chosen.all()).contains("gardening-001-captain-disagrees.md", "gardening-006-question-left-open.md", "gardening-002-merged-against-a-warning.md");
        assertThat(board.output()).isEqualTo("Wrote " + round.resolve("board.html") + ", asking 6 questions: slip-1, slip-2, slip-3, rules, hygiene, sparks.\n");
        String html = Files.readString(round.resolve("board.html"));
        assertThat(html).contains("<h3>1. What the captain marked with &quot;(!)&quot; is refused by the core from now on</h3>",
                "<h3>2. An unanswered worker question comes back until it is answered</h3>",
                "data-lavish-question=\"slip-3\"", "guard.harbour-lights", "Waits for a calmer sea", "Keep an anchor on the bow");
        assertThat(Files.readString(round.resolve("drafts/gardening-006-question-left-open.md")))
                .startsWith("---\ntitle: An unanswered worker question comes back until it is answered\nkind: ship\n")
                .contains("source: The gardening round of the week of 2026-10-12, after question-left-open on fit-lamp\n", "TODO: the hard rule");
        assertThat(script("choose", round.toString(), "99").all()).contains("Choose up to 3 slips by their numbers, 1 to 15; 99 cannot be chosen.");
    }

    private void inventTheWeek() throws IOException {
        inventThePreviousWeek();
        aQuestionLeftOpenAndAGateParked();
        aQuestionAskedAgainAfterARelaunchAndLeftWhenRetired();
        aRequestMergedWhileChangingAfterFiveRounds();
        aRequestMergedCleanlyAndThenFixed();
        twoDecisionsAnsweredOneAgainstItsOptions();
        rulesFiringOftenAndOnTheCaptainsWord();
        theCaptainAndThePrimaryTalking();
        hygieneDrifting();
    }

    private void inventThePreviousWeek() throws IOException {
        reports("2026-10-07T10:00:00Z", "fit-lamp", "needs-decision", "bulb", "Which bulb?");
        event("2026-10-07T10:04:00Z", "primary", "SteerSent", "fit-lamp", "{\"number\":1,\"resolves\":\"bulb\"}");
        fires("2026-10-07T09:00:00Z", "guard.forced-push", "refused");
        captainWrites("2026-10-07T11:00:00Z", "I said bayonet.");
        primaryRuns("2026-10-07T11:01:00Z", "edi report --turn -");
    }

    private void aQuestionLeftOpenAndAGateParked() {
        reports("2026-10-13T10:00:00Z", "fit-lamp", "needs-decision", "socket", "Which socket?");
        event("2026-10-13T10:25:00Z", "primary", "SteerSent", "fit-lamp", "{\"number\":2,\"resolves\":\"socket\"}");
        event("2026-10-13T10:26:00Z", "worker:fit-lamp#1", "SteerAcknowledged", "fit-lamp", "{\"number\":2}");
        reports("2026-10-13T11:00:00Z", "tune-horn", "needs-decision", "nm-01RUN-review", "Fix the finding or skip it?");
        event("2026-10-13T11:45:00Z", "primary", "SteerSent", "tune-horn", "{\"number\":1,\"resolves\":\"nm-01RUN-review\"}");
        event("2026-10-13T11:46:00Z", "worker:tune-horn#1", "SteerAcknowledged", "tune-horn", "{\"number\":1}");
    }

    private void aQuestionAskedAgainAfterARelaunchAndLeftWhenRetired() {
        reports("2026-10-18T10:00:00Z", "light-beacon", "needs-decision", "lens", "Which lens?");
        event("2026-10-18T10:01:00Z", "core", "WorkerRelaunched", "light-beacon", "{\"worker\":\"light-beacon#2\",\"because\":\"the machine restarted\"}");
        reportsAs("2026-10-18T10:02:00Z", "light-beacon#2", "light-beacon", "needs-decision", "lens", "Which lens?");
        event("2026-10-18T10:20:00Z", "primary", "WorkerRetired", "light-beacon", "{\"worker\":\"light-beacon#2\",\"because\":\"its task was cleaned up\"}");
    }

    private void aRequestMergedWhileChangingAfterFiveRounds() {
        reports("2026-10-14T09:00:00Z", "tune-horn", "done", null, "https://github.com/example/harness/pull/4");
        event("2026-10-14T10:00:00Z", "primary", "SteerSent", "tune-horn", "{\"number\":2,\"resolves\":null}");
        reports("2026-10-14T10:00:00Z", "tune-horn", "working", null, "Tuning it again.");
        event("2026-10-14T11:30:00Z", "primary", "DraftMerged", "tune-horn", "{\"url\":\"https://github.com/example/harness/pull/4\",\"landedCommit\":\"abc\"}");
        event("2026-10-14T11:31:00Z", "primary", "ReviewCounted", "tune-horn", "{\"rounds\":5,\"findings\":7}");
    }

    private void aRequestMergedCleanlyAndThenFixed() {
        reports("2026-10-15T09:00:00Z", "paint-hull", "done", null, "https://github.com/example/harness/pull/5");
        event("2026-10-15T09:30:00Z", "primary", "DraftMerged", "paint-hull", "{\"url\":\"https://github.com/example/harness/pull/5\",\"landedCommit\":\"def\"}");
        event("2026-10-15T09:31:00Z", "primary", "ReviewCounted", "paint-hull", "{\"rounds\":2,\"findings\":1}");
        home.storing(List.of(WorkerFixtures.queuedShip("fix-hull", "harness", "HRN-5")));
        event("2026-10-16T08:00:00Z", "primary", "TaskAdded", "fix-hull", "{\"kind\":\"ship\",\"title\":\"Fix the hull's paint\",\"workKey\":\"HRN-5\"}");
    }

    private void twoDecisionsAnsweredOneAgainstItsOptions() {
        decision(3, "fit-lamp");
        decision(4, "tune-horn");
        event("2026-10-16T09:00:00Z", "captain", "DecisionAnswered", "fit-lamp",
                "{\"number\":\"D3\",\"verbatim\":\"Neither: light it by hand.\",\"option\":null,\"interpretation\":\"By hand.\"}");
        event("2026-10-16T09:05:00Z", "captain", "DecisionAnswered", "tune-horn",
                "{\"number\":\"D4\",\"verbatim\":\"A\",\"option\":\"A\",\"interpretation\":\"The low note.\"}");
    }

    private void rulesFiringOftenAndOnTheCaptainsWord() {
        for (int minute = 0; minute < 10; minute++) {
            fires("2026-10-17T09:0" + minute + ":00Z", "guard.forced-push", "refused");
        }
        fires("2026-10-17T10:00:00Z", "authority.captains-word", "refused");
    }

    private void theCaptainAndThePrimaryTalking() throws IOException {
        captainWrites("2026-10-13T12:00:00Z", "(!) \"The lamp is lit\" - it is not.");
        captainWrites("2026-10-14T12:00:00Z", "Why is the horn still out of tune?");
        primaryWrites("2026-10-14T12:01:00Z", "That was my mistake: I missed the horn's question.");
        captainWrites("2026-10-15T12:00:00Z", "(?) \"I parked the hull\" - why?");
        captainWrites("2026-10-15T12:05:00Z", "Paint the hull blue.");
        appendLine("{\"type\":\"user\",\"uuid\":\"hook-1\",\"timestamp\":\"2026-10-15T12:06:00Z\",\"sessionId\":\"" + SESSION
                + "\",\"isMeta\":true,\"message\":{\"role\":\"user\",\"content\":\"Stop hook feedback:\\nRender the turn report.\"}}");
        appendLine("{\"type\":\"user\",\"uuid\":\"summary-1\",\"timestamp\":\"2026-10-15T12:07:00Z\",\"sessionId\":\"" + SESSION
                + "\",\"isCompactSummary\":true,\"message\":{\"role\":\"user\",\"content\":\"This session is being continued; I said a lot.\"}}");
        for (int time = 0; time < 5; time++) {
            primaryRuns("2026-10-16T1" + time + ":00:00Z", "edi worker list");
            primaryRuns("2026-10-16T1" + time + ":01:00Z", "edi task show fit-lamp");
        }
        primaryRuns("2026-10-17T11:00:00Z", "edi report --turn -");
        primaryRuns("2026-10-17T11:05:00Z", "cd /tmp && edi report --turn -");
    }

    private void hygieneDrifting() throws IOException {
        home.putAt(new TaskId("paint-hull"), TaskStage.MERGED);
        home.putAt(new TaskId("sink-buoy"), TaskStage.DROPPED);
        home.putInFlight(new TaskId("fit-lamp"));
        hold("light-beacon", "Waits for a calmer sea", "2026-09-20T08:00:00Z");
        hold("fit-lamp", "Backlogged upstream as https://github.com/example/edi/issues/9", "2026-09-20T08:00:00Z");
        Files.createDirectories(home.home().worktrees().resolve("harness/paint-hull"));
        Files.createDirectories(home.home().worktrees().resolve("harness/sink-buoy"));
        Files.createDirectories(home.home().worktrees().resolve("harness/fit-lamp"));
        event("2026-10-15T09:31:00Z", "core", "TicketTransitioned", "fit-lamp", "{\"key\":\"HRN-3\",\"from\":\"To Do\",\"to\":\"Test in DEV\"}");
        home.jdbc().sql("insert into memory_check (checked_at, tokens, budget) values ('2026-10-19T08:00:00Z', 17000, 18000)").update();
        home.jdbc().sql("insert into spark (number, text, author, caught_at) values (1, 'Keep an anchor on the bow', 'captain', '2026-10-16T08:00:00Z')")
                .update();
        theEdiClone();
    }

    private void theEdiClone() throws IOException {
        FixtureRepository.initialise(Files.createDirectories(home.home().root().resolve("projects/edi")))
                .write("docs/rules.md", CATALOGUE)
                .write("src/Keeper.java", "class RemovedKeeper {\n}\n")
                .commit("Keep the lamps")
                .remove("src/Keeper.java")
                .write(LAMPS_RECORD, "# Lamps\n\n## Decision\n\n`HarbourLamp` lights itself, `RemovedKeeper` checks it, a `ThreadPoolTaskScheduler` times it"
                        + " and `UserPromptSubmit` wakes it.\n\n## Alternatives rejected\n\n- `OldBeacon` - too dim.\n")
                .write("claude/settings.json", "{\"hooks\": {\"UserPromptSubmit\": []}}\n")
                .write("docs/jira/HRN-3.md", "---\nkey: HRN-3\n---\n\n# HRN-3 Fit the lamp\n\n**Type:** Story **Status:** To Do\n")
                .write("src/Lamp.java", "class HarbourLamp {\n    // TODO EDI-7 light it at dusk\n    // TODO EDI-5 dim it at dawn\n}\n")
                .commit("Start the harbour")
                .configure("remote.origin.url", "https://github.com/example/edi.git")
                .branch("feature/EDI-9")
                .branch("feature/HRN-3")
                .switchTo("main");
    }

    private void reports(String at, String task, String state, String key, String text) {
        reportsAs(at, task + "#1", task, state, key, text);
    }

    private void reportsAs(String at, String worker, String task, String state, String key, String text) {
        event(at, "worker:" + worker, "StatusReported", task, JSON.writeValueAsString(
                key == null ? Map.of("worker", worker, "state", state, "text", text) : Map.of("worker", worker, "state", state, "key", key, "text", text)));
    }

    private void event(String at, String actor, String type, String task, String payload) {
        home.jdbc().sql("insert into event (at, actor, type, task_id, payload) values (:at, :actor, :type, :task, :payload)")
                .param("at", at).param("actor", actor).param("type", type).param("task", task).param("payload", payload)
                .update();
    }

    private void fires(String at, String rule, String outcome) {
        home.jdbc().sql("insert into rule_fired (at, rule, outcome, actor, reason) values (:at, :rule, :outcome, 'worker', 'a reason')")
                .param("at", at).param("rule", rule).param("outcome", outcome)
                .update();
    }

    private void decision(int number, String task) {
        home.jdbc().sql("insert into decision (number, task_id, title, context, recommended, recommended_because, raised_at)"
                + " values (:number, :task, 'Which light?', 'The lamp.', 'A', 'Brighter.', '2026-10-16T08:00:00Z')")
                .param("number", number).param("task", task)
                .update();
        for (String option : List.of("A", "B")) {
            home.jdbc().sql("insert into decision_option (decision_number, position, option_key, option_text) values (:number, :position, :option, :option)")
                    .param("number", number).param("position", option.charAt(0) - 'A').param("option", option)
                    .update();
        }
    }

    private void hold(String task, String reason, String setAt) {
        home.jdbc().sql("insert into captain_hold (task_id, kind, reason, until, set_at) values (:task, 'deferral', :reason, null, :setAt)")
                .param("task", task).param("reason", reason).param("setAt", setAt)
                .update();
    }

    private void captainWrites(String at, String text) throws IOException {
        appendLine(JSON.writeValueAsString(Map.of("type", "user", "uuid", "line-" + ++lines, "timestamp", at, "sessionId", SESSION,
                "origin", Map.of("kind", "human"), "message", Map.of("role", "user", "content", text))));
    }

    private void primaryWrites(String at, String text) throws IOException {
        appendLine(JSON.writeValueAsString(Map.of("type", "assistant", "uuid", "line-" + ++lines, "timestamp", at, "sessionId", SESSION,
                "message", Map.of("role", "assistant", "content", List.of(Map.of("type", "text", "text", text))))));
    }

    private void primaryRuns(String at, String command) throws IOException {
        appendLine(JSON.writeValueAsString(Map.of("type", "assistant", "uuid", "line-" + ++lines, "timestamp", at, "sessionId", SESSION,
                "message", Map.of("role", "assistant", "content", List.of(Map.of("type", "tool_use", "id", "call-" + lines, "name", "Bash",
                        "input", Map.of("command", command)))))));
    }

    private void appendLine(String line) throws IOException {
        Files.writeString(transcripts.resolve(SESSION + ".jsonl"), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private JsonNode gather() throws IOException, InterruptedException {
        Run gathered = script("gather", "--week", WEEK, "--out", round.toString(), "--home", home.home().root().toString(), "--transcripts",
                transcripts.toString(), "--repository", home.home().root().resolve("projects/edi").toString());
        assertThat(gathered.exitCode()).as(gathered.all()).isZero();
        return JSON.readTree(Files.readString(round.resolve("round.json")));
    }

    private Run script(String... arguments) throws IOException, InterruptedException {
        StandInGitHub github = StandInGitHub.serving(directory.resolve("github"), WORLD);
        return github.run(processes, directory, Map.of("TZ", "UTC", "GIT_CONFIG_GLOBAL", "/dev/null", "GIT_CONFIG_NOSYSTEM", "1"),
                StandInGitHub.node(SCRIPT, arguments));
    }

    private static List<String> texts(JsonNode slips) {
        List<String> texts = new ArrayList<>();
        slips.forEach(slip -> texts.add(slip.get("kind").asString() + ": " + slip.get("text").asString()));
        return texts;
    }
}
