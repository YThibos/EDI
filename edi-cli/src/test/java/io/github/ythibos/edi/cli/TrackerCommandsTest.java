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
class TrackerCommandsTest {

    @TempDir
    private Path home;

    private StubCore core;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void startTheStubCore() throws IOException {
        core = StubCore.recordedIn(home);
    }

    @AfterEach
    void stopTheStubCore() {
        core.close();
    }

    @Test
    void asksTheCoreToWalkATicketToAStateAndPrintsItsAnswer() {
        core.answering(200, "EXAMPLE-7 moved Done -> Reopened -> Backlog in 2 steps.\n");

        int walked = edi("tracker", "walk", "EXAMPLE-7", "Ready For Prd");

        StubCore.Request request = core.received();
        assertThat(walked).isZero();
        assertThat(request.target()).isEqualTo("/api/tracker/walk");
        assertThat(request.body()).isEqualTo("{\"key\":\"EXAMPLE-7\",\"state\":\"Ready For Prd\"}");
        assertThat(out).hasToString("EXAMPLE-7 moved Done -> Reopened -> Backlog in 2 steps.\n");
    }

    @Test
    void failsWithTheStepsTakenWhenTheCoreStopsTheWalk() {
        core.answering(409, "EXAMPLE-7 stopped at Backlog after the steps Done -> Reopened -> Backlog on its way to Ready For Prd: JIRA refused it.\n");

        int walked = edi("tracker", "walk", "EXAMPLE-7", "Ready For Prd");

        assertThat(walked).isOne();
        assertThat(err.toString()).startsWith("EXAMPLE-7 stopped at Backlog after the steps Done -> Reopened -> Backlog");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void asksTheCoreForTheDriftOfAProject() {
        core.answering(200, "Ticket drift in example-service (0)\n");

        int listed = edi("tracker", "drift", "example-service");

        assertThat(listed).isZero();
        assertThat(core.received().target()).isEqualTo("/api/tracker/drift?project=example-service");
        assertThat(out).hasToString("Ticket drift in example-service (0)\n");
    }

    @Test
    void wantsASubcommandAndBothArgumentsOfAWalk() {
        int bare = edi("tracker");
        int withoutState = edi("tracker", "walk", "EXAMPLE-7");

        assertThat(bare).isEqualTo(2);
        assertThat(withoutState).isEqualTo(2);
        assertThat(core.requests()).isEmpty();
    }

    private int edi(String... arguments) {
        return new CommandLine(new Edi(Map.of("EDI_HOME", home.toString()), new ByteArrayInputStream(new byte[0])))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }
}
