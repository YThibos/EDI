package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

@Tag("adapter")
class DeliveryCommandsTest {

    @TempDir
    private Path home;

    private StubCore core;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    void startTheStubCore() throws IOException {
        core = StubCore.recordedIn(home);
        environment.put("EDI_HOME", home.toString());
    }

    @AfterEach
    void stopTheStubCore() {
        core.close();
    }

    @Test
    void deliversATasksDraftAndCleansUpATask() {
        core.answering(200, "Opened the draft.\n");

        assertThat(edi("deliver", "fit-lamp")).isZero();
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/deliver");
        assertThat(out).hasToString("Opened the draft.\n");

        edi("cleanup", "D58");
        assertThat(core.received().target()).isEqualTo("/api/tasks/D58/cleanup");
        assertThat(core.received().method()).isEqualTo("POST");
    }

    @Test
    void tellsTheCoreWhatTheCaptainMergedByTaskOrByUrl() {
        core.answering(200, "Recorded the merge.\n");

        edi("merged", "https://github.com/example-owner/harness/pull/7");

        assertThat(core.received().target()).isEqualTo("/api/merged");
        assertThat(core.received().body()).isEqualTo("{\"reference\":\"https://github.com/example-owner/harness/pull/7\"}");
    }

    @Test
    void showsACleanUpRefusalAndExitsWithAFailure() {
        core.answering(409, "fit-lamp is not cleaned up, because its work is not landed: lamp.txt is not committed.\n");

        assertThat(edi("cleanup", "fit-lamp")).isNotZero();
        assertThat(err.toString()).contains("lamp.txt is not committed");
    }

    @Test
    void namesThePushTargetForThePublishCheckAndADeliveryModeForANewTask() {
        core.answering(200, "ok\n");

        edi("publish-check", "--range", "origin/main..HEAD", "--remote-url", "https://github.com/example-owner/harness.git");
        assertThat(core.received().body()).endsWith("\"range\":\"origin/main..HEAD\",\"remoteUrl\":\"https://github.com/example-owner/harness.git\"}");

        edi("task", "add", "--kind", "ship", "--project", "harness", "--title", "Fit the lamp", "--delivery", "local-apply");
        assertThat(core.received().body()).contains("\"delivery\":\"local-apply\"");
    }

    private int edi(String... arguments) {
        return new CommandLine(new Edi(Map.copyOf(environment), new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }
}
