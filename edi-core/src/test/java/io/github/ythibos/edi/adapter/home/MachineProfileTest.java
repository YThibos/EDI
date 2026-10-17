package io.github.ythibos.edi.adapter.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.domain.worker.PermissionMode;
import io.github.ythibos.edi.testing.ExampleHome;

@Tag("adapter")
class MachineProfileTest {

    @TempDir
    private Path home;

    @Test
    void readsTheWorkerSettingsOfTheExampleProfile() throws IOException {
        WorkerSettings workers = profileOf(ExampleHome.file("config/machine.yaml")).workers();

        assertThat(workers.models().strongestFirst()).hasSize(3);
        assertThat(workers.disallowedTools()).isEmpty();
        assertThat(workers.permissionMode()).isEqualTo(PermissionMode.BYPASS);
    }

    @Test
    void allowsNoModelWithoutAProfileAndRefusesAMalformedToolList() throws IOException {
        assertThat(new MachineProfile(new EdiHome(home)).workers().models().strongestFirst()).isEmpty();

        for (String malformed : List.of("\"Web Search\"", "\"Bash(git push\"", "\"Bash(x)(y)\"", "\"\"")) {
            MachineProfile profile = profileOf("workers:\n  disallowedTools: [WebFetch, " + malformed + "]\n");
            assertThatThrownBy(profile::workers).isInstanceOf(InvalidConfiguration.class).hasMessageContaining("is not a tool name");
        }
        assertThat(profileOf("workers:\n  disallowedTools: [WebFetch, \"Bash(git push:*)\", mcp__forge__merge]\n  permissionMode: auto\n").workers())
                .extracting(WorkerSettings::permissionMode).isEqualTo(PermissionMode.AUTO);
        assertThatThrownBy(() -> profileOf("workers:\n  permissionMode: yolo\n").workers()).hasMessageContaining("bypass, auto or prompt");
    }

    @Test
    void readsTheTrackerAndTimeOfTheExampleProfile() throws IOException {
        MachineProfile profile = profileOf(ExampleHome.file("config/machine.yaml"));

        TrackerSettings tracker = profile.tracker();
        assertThat(tracker.kind()).isEqualTo(TrackerKind.JIRA);
        assertThat(tracker.baseUrl()).hasToString("https://jira.example.org");
        assertThat(tracker.credentials()).isEqualTo(new TrackerSettings.Credentials(Path.of("/path/to/api-keys.yml"), "JIRA"));
        assertThat(tracker.evergreen()).containsExactlyInAnyOrder(WorkKey.parse("EXAMPLE-1"), WorkKey.parse("EXAMPLE-2"));
        assertThat(tracker.mergedStatus()).isEqualTo("In Test");
        assertThat(profile.time()).isEqualTo(new TimeSettings(true, true));
    }

    @Test
    void readsNoTrackerAndNoTimeWithoutThoseSections() throws IOException {
        MachineProfile desktop = profileOf("tracker:\n  kind: none\ntime:\n  fleetLedger: false\n");

        assertThat(desktop.tracker()).isEqualTo(TrackerSettings.none());
        assertThat(desktop.time()).isEqualTo(new TimeSettings(false, false));
        assertThat(new MachineProfile(new EdiHome(home.resolve("elsewhere"))).tracker().isJira()).isFalse();
    }

    @Test
    void refusesAJiraTrackerItCouldNotReachOrMoveSafely() throws IOException {
        String complete = """
                tracker:
                  kind: jira
                  baseUrl: https://jira.example.org
                  credentials: { keysFile: /keys.yml, section: JIRA }
                  workflow: [To Do, Done]
                  mergedStatus: Done
                  issueType: Story
                """;
        assertThat(profileOf(complete).tracker().isJira()).isTrue();

        for (String broken : List.of(complete.replace("https://jira", "http://jira"), complete.replace("  credentials: { keysFile: /keys.yml, section: JIRA }\n", ""),
                complete.replace("keysFile: /keys.yml", "keysFile: keys.yml"), complete.replace("mergedStatus: Done", "mergedStatus: Released"),
                complete.replace("  issueType: Story\n", ""), complete.replace("kind: jira", "kind: edi"))) {
            MachineProfile profile = profileOf(broken);
            assertThatThrownBy(profile::tracker).isInstanceOf(InvalidConfiguration.class);
        }
    }

    private MachineProfile profileOf(String yaml) throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/machine.yaml"), yaml);
        return new MachineProfile(new EdiHome(home));
    }
}
