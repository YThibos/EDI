package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

@Tag("adapter")
class UpCommandTest {

    private static final PrimaryLaunch LAUNCH = new PrimaryLaunch("edi-261118-0900", Path.of("/edi/home"), Path.of("/edi/home/settings.json"));

    @TempDir
    private Path home;

    private StubCore core;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void startTheStubCore() throws IOException {
        core = StubCore.recordedIn(home);
        core.answering(200, "ok\n");
    }

    @AfterEach
    void stopTheStubCore() {
        core.close();
    }

    @Test
    void startsOnlyTheCoreAndAsksItForNoPrimarySessionWhenToldCoreOnly() {
        int exitCode = commandLine().execute("up", "--core-only");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).isEqualTo("EDI core is already up.\n");
        assertThat(err.toString()).isEmpty();
        assertThat(core.requests()).extracting(StubCore.Request::target).containsExactly("/api/health");
    }

    @Test
    void launchesThePrimaryTellingTheCoreToLeaveTheBoardClosedOnlyWhenToldNoBoard() {
        PrimaryWindow withoutTheBoard = upCommandFor("up", "--no-board").primaryWindow();
        PrimaryWindow plain = upCommandFor("up").primaryWindow();

        assertThat(withoutTheBoard.newSession(LAUNCH)).containsSequence("env", "EDI_PRIMARY_LAUNCH=edi-up-no-board");
        assertThat(plain.newSession(LAUNCH)).containsSequence("env", "EDI_PRIMARY_LAUNCH=edi-up");
    }

    private UpCommand upCommandFor(String... arguments) {
        return (UpCommand) commandLine().parseArgs(arguments).subcommand().commandSpec().userObject();
    }

    private CommandLine commandLine() {
        return new CommandLine(new Edi(Map.of("EDI_HOME", home.toString()), new ByteArrayInputStream(new byte[0])))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true));
    }
}
