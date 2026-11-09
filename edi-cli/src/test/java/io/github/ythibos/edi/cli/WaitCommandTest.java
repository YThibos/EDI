package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.Patience;

import picocli.CommandLine;

@Tag("adapter")
class WaitCommandTest {

    private static final String SESSION = "6d1f0c2e-0000-4000-8000-000000000001";
    private static final String LAMP_DONE = "EDI wake: 1 waiting.\n7. fit-lamp#1 reports done: fitted\n";
    private static final String HORN_BLOCKED = "EDI wake: 1 waiting.\n8. tune-horn#1 is blocked: no reed\n";
    private static final String REFUSED = "edi wait is refused: the primary's wake stream is already live as edi wait --follow in process 4242.\n";
    private static final String WRITE_ONLY = "0100001";
    private static final String READ_ONLY = "0100000";

    @TempDir
    private Path home;

    private StubCore core;
    private StandardOutput readOutput;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    void startTheStubCoreInThePrimarysSession() throws IOException {
        core = StubCore.recordedIn(home);
        environment.put("EDI_HOME", home.toString());
        environment.put("CLAUDE_CODE_SESSION_ID", SESSION);
        readOutput = descriptorOpenedOn(Files.createFile(home.resolve("monitor.output")), WRITE_ONLY);
    }

    @AfterEach
    void stopTheStubCore() {
        core.close();
    }

    @Test
    void waitsUntilTheCoreHasAWakePrintsItAndTellsTheCoreItWasPrinted() {
        core.thenAnswering(204, "").thenAnswering(204, "").thenWaking(7, LAMP_DONE).thenAnswering(204, "");

        int exitCode = edi(readOutput, "wait");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString(LAMP_DONE);
        assertThat(core.requests()).hasSize(4).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.target()).isEqualTo("/api/wait");
            assertThat(request.session()).isEqualTo(SESSION);
            assertThat(request.body()).contains("\"stream\":\"", "\"pid\":" + ProcessHandle.current().pid()).doesNotContain("\"follows\"");
        });
        assertThat(core.requests().getLast().body()).endsWith("\"printed\":[7],\"ending\":true}");
        assertThat(core.requests()).extracting(StubCore.Request::body).map(WaitCommandTest::streamIn).containsOnly(streamIn(core.requests().getFirst().body()));
    }

    @Test
    void followsTheWakesPrintingEachAsItComesAndEndsLoudlyWhenTheCoreRefuses() {
        core.thenWaking(7, LAMP_DONE).thenAnswering(204, "").thenWaking(8, HORN_BLOCKED).thenAnswering(409, REFUSED);

        int exitCode = edi(readOutput, "wait", "--follow");

        assertThat(exitCode).isOne();
        assertThat(out).hasToString(LAMP_DONE + HORN_BLOCKED + "The wake stream ended: " + REFUSED);
        assertThat(core.requests()).extracting(StubCore.Request::body).map(WaitCommandTest::printedIn)
                .containsExactly("[]", "[7]", "[]", "[8]");
        assertThat(core.requests()).allSatisfy(request -> assertThat(request.body()).contains("\"follows\":true"));
    }

    @Test
    void keepsFollowingAcrossARestartOfTheCoreAndTellsTheNewCoreWhatItPrinted() throws Exception {
        core.thenWaking(7, LAMP_DONE).droppingEveryCallAfter(1);

        CompletableFuture<Integer> following = CompletableFuture.supplyAsync(() -> edi(readOutput, "wait", "--follow"));
        assertThat(core.awaitRequests(2)).isTrue();
        try (StubCore restarted = StubCore.recordedIn(home)) {
            restarted.thenWaking(8, HORN_BLOCKED).thenAnswering(409, REFUSED);
            int exitCode = following.get(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS);

            assertThat(exitCode).isOne();
            assertThat(out).hasToString(LAMP_DONE + HORN_BLOCKED + "The wake stream ended: " + REFUSED);
            assertThat(restarted.requests()).extracting(StubCore.Request::body).map(WaitCommandTest::printedIn).containsExactly("[7]", "[8]");
            assertThat(restarted.requests()).extracting(StubCore.Request::body).map(WaitCommandTest::streamIn)
                    .containsOnly(streamIn(core.requests().getFirst().body()));
        }
    }

    @Test
    void refusesToStartWhenItsStandardOutputLeadsNowhere() throws IOException {
        Path intoTheVoid = Files.createSymbolicLink(home.resolve("void"), Path.of("/dev/null"));
        StandardOutput closed = new StandardOutput(home.resolve("closed"));

        int voided = edi(new StandardOutput(intoTheVoid), "wait", "--follow");
        Files.delete(intoTheVoid);
        String voidedError = err.toString();
        int shut = edi(closed, "wait");

        assertThat(voided).isOne();
        assertThat(voidedError).startsWith("edi wait refuses to start: its standard output is /dev/null, so every wake it took would be lost.")
                .contains("the Monitor tool running edi wait --follow alone");
        assertThat(shut).isOne();
        assertThat(err.toString()).contains("edi wait refuses to start: its standard output is closed");
        assertThat(out.toString()).isEmpty();
        assertThat(core.requests()).extracting(StubCore.Request::target).containsExactly("/api/rules/fired", "/api/rules/fired");
    }

    @Test
    void refusesToStartWhenItsStandardOutputIsOpenOnlyForReadingAsAClosedDescriptorReusedByTheRuntime() throws IOException {
        StandardOutput reused = descriptorOpenedOn(Files.createFile(home.resolve("modules")), READ_ONLY);

        int exitCode = edi(reused, "wait", "--follow");

        assertThat(exitCode).isOne();
        assertThat(err.toString()).startsWith("edi wait refuses to start: its standard output is closed");
        assertThat(out.toString()).isEmpty();
        assertThat(core.requests()).extracting(StubCore.Request::target).containsExactly("/api/rules/fired");
    }

    @Test
    void waitsOnlyInsideAClaudeCodeSessionAndStopsWhenTheCoreRefuses() {
        environment.remove("CLAUDE_CODE_SESSION_ID");
        int outside = edi(readOutput, "wait");
        String outsideError = err.toString();
        environment.put("CLAUDE_CODE_SESSION_ID", SESSION);
        core.answering(409, "edi wait serves the primary session, the one the captain typed in last; this session is not it.\n");
        int refused = edi(readOutput, "wait");

        assertThat(outside).isOne();
        assertThat(outsideError).startsWith("edi wait runs inside the primary's Claude Code session");
        assertThat(refused).isOne();
        assertThat(err.toString()).endsWith("this session is not it.\n");
    }

    private StandardOutput descriptorOpenedOn(Path target, String flags) throws IOException {
        Path process = Files.createTempDirectory(home, "proc-");
        Files.writeString(Files.createDirectories(process.resolve("fdinfo")).resolve("1"), "pos:\t0\nflags:\t" + flags + "\nmnt_id:\t88\n");
        return new StandardOutput(Files.createSymbolicLink(Files.createDirectories(process.resolve("fd")).resolve("1"), target));
    }

    private int edi(StandardOutput output, String... arguments) {
        return new CommandLine(new Edi(Map.copyOf(environment), new ByteArrayInputStream(new byte[0]), output))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }

    private static String printedIn(String body) {
        int start = body.indexOf("\"printed\":") + "\"printed\":".length();
        return body.substring(start, body.indexOf(']', start) + 1);
    }

    private static String streamIn(String body) {
        return List.of(body.split("\"")).get(3);
    }
}
