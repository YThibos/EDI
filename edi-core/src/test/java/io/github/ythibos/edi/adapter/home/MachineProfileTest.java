package io.github.ythibos.edi.adapter.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.domain.worker.LoginName;
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
        assertThat(tracker.credentials()).isEqualTo(new TrackerSettings.Credentials(Path.of("/path/to/tracker-keys.yml"), "JIRA"));
        assertThat(tracker.evergreen()).containsExactlyInAnyOrder(WorkKey.parse("EXAMPLE-1"), WorkKey.parse("EXAMPLE-2"));
        assertThat(tracker.evergreenTicket("tooling")).contains(WorkKey.parse("EXAMPLE-2"));
        assertThat(tracker.evergreenTicket("travel")).isEmpty();
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

    @Test
    void readsTheWindowsSideAndTheMemoryBudget() throws IOException {
        MachineProfile example = profileOf(ExampleHome.file("config/machine.yaml"));
        MachineProfile bare = new MachineProfile(new EdiHome(home.resolve("bare")));

        assertThat(example.windowsSide()).isEqualTo(new WindowsSide(true, true));
        assertThat(example.startupMemoryBudgetTokens()).isEqualTo(18_000);
        assertThat(bare.windowsSide()).isEqualTo(new WindowsSide(false, false));
        assertThat(bare.startupMemoryBudgetTokens()).isEqualTo(18_000);
        assertThat(profileOf("memory:\n  startupBudgetTokens: 9000\npresence:\n  calendar: none\n").windowsSide().outlookCalendar()).isFalse();
    }

    @Test
    void opensTheSitrepAtSessionStartUnlessTheProfileSwitchesItOff() throws IOException {
        MachineProfile bare = new MachineProfile(new EdiHome(home.resolve("bare")));

        assertThat(bare.opensTheSitrepAtSessionStart()).isTrue();
        assertThat(profileOf(ExampleHome.file("config/machine.yaml")).opensTheSitrepAtSessionStart()).isTrue();
        assertThat(profileOf("console:\n  openSitrepAtSessionStart: false\n").opensTheSitrepAtSessionStart()).isFalse();
    }

    @Test
    void addsOnlyTheCarriedSettingsTheProfileLacks() throws IOException {
        CarriedMachineSettings carried = new CarriedMachineSettings(MachineProfile.workerRosterIn(ExampleHome.file("config/machine.yaml")), true, 18_000,
                Duration.ofHours(4));
        String partial = "machine: laptop\ntime:\n  fleetLedger: false\n";

        String added = profileOf(partial).contentWith(carried);
        Files.writeString(home.resolve("config/machine.yaml"), added);

        assertThat(profileOf(added).time().fleetLedger()).as("the profile's own setting").isFalse();
        assertThat(profileOf(added).startupMemoryBudgetTokens()).isEqualTo(18_000);
        assertThat(profileOf(added).supervision().pauseResurface()).isEqualTo(Duration.ofHours(4));
        assertThat(profileOf(added).workers().models().strongestFirst()).extracting(model -> model.id().value(), model -> model.takesEffort())
                .containsExactly(tuple("claude-opus-5-5", true), tuple("claude-sonnet-5-5", true), tuple("claude-haiku-5-5", false));
        assertThat(profileOf(added).name()).isEqualTo("laptop");
        assertThat(profileOf(added).contentWith(carried)).as("a profile that already holds every setting").isEqualTo(added);
        assertThat(new MachineProfile(new EdiHome(home.resolve("bare"))).contentWith(carried)).contains("fleetLedger: true", "startupBudgetTokens: 18000");
    }

    @Test
    void readsAnEditedProfileAtItsNextCallAndRefusesItWhileItIsMalformed() throws IOException {
        MachineProfile profile = profileOf("time:\n  fleetLedger: true\n");
        boolean before = profile.time().fleetLedger();

        Files.writeString(home.resolve("config/machine.yaml"), "time:\n  fleetLedger: false\n");
        boolean edited = profile.time().fleetLedger();
        Files.writeString(home.resolve("config/machine.yaml"), "time: [\n");

        assertThat(before).isTrue();
        assertThat(edited).isFalse();
        assertThatThrownBy(profile::time).isInstanceOf(InvalidConfiguration.class);
        assertThatThrownBy(profile::time).as("a malformed profile asked again").isInstanceOf(InvalidConfiguration.class);
    }

    @Test
    void readsHowOftenAnUnansweredWorkerQuestionIsRaisedAgainAndRefusesNone() throws IOException {
        Duration absent = profileOf("machine: laptop\n").supervision().questionReminder();
        Duration set = profileOf("supervision:\n  questionReminder: PT5M\n").supervision().questionReminder();
        MachineProfile never = profileOf("supervision:\n  questionReminder: PT0S\n");

        assertThat(absent).isEqualTo(Duration.ofMinutes(10));
        assertThat(set).isEqualTo(Duration.ofMinutes(5));
        assertThatThrownBy(never::supervision).isInstanceOf(InvalidConfiguration.class).hasMessageContaining("supervision.questionReminder");
    }

    @Test
    void mapsEachClaudeLoginToItsConfigurationDirectoryAndKeepsTheDefaultInClaudeCodesOwnUnlessItIsNamed() throws IOException {
        ClaudeLogins example = profileOf(ExampleHome.file("config/machine.yaml")).claudeLogins();
        assertThat(example.named(new LoginName("hobby"))).isEqualTo(new Login(new LoginName("hobby"), Path.of(System.getProperty("user.home"), ".claude-hobby")));

        ClaudeLogins none = profileOf("machine: laptop\n").claudeLogins();
        assertThat(none.named(LoginName.DEFAULT).keepsClaudeCodesOwnDirectory()).isTrue();
        assertThat(none.names(new LoginName("hobby"))).isFalse();
        assertThatThrownBy(() -> none.named(new LoginName("hobby"))).hasMessageContaining("names no Claude login hobby under claude.logins");

        ClaudeLogins named = profileOf("claude:\n  logins:\n    default: { configDirectory: /logins/main }\n    hobby: { configDirectory: ~/.claude-hobby }\n")
                .claudeLogins();
        assertThat(named.named(LoginName.DEFAULT).configDirectory()).isEqualTo(Path.of("/logins/main"));
        assertThat(named.names(new LoginName("hobby"))).isTrue();
    }

    @Test
    void refusesALoginWithoutAnAbsoluteConfigurationDirectoryOrWithAMalformedName() throws IOException {
        assertThatThrownBy(profileOf("claude:\n  logins:\n    hobby: {}\n")::claudeLogins)
                .isInstanceOf(InvalidConfiguration.class).hasMessageContaining("claude.logins.hobby names no configDirectory");
        assertThatThrownBy(profileOf("claude:\n  logins:\n    hobby: { configDirectory: logins/hobby }\n")::claudeLogins).isInstanceOf(InvalidConfiguration.class)
                .hasMessageContaining("claude.logins.hobby.configDirectory is \"logins/hobby\", which is neither an absolute path nor one under ~/.");
        assertThatThrownBy(profileOf("claude:\n  logins:\n    Hobby Login: { configDirectory: /logins/hobby }\n")::claudeLogins)
                .isInstanceOf(InvalidConfiguration.class).hasMessageContaining("Hobby Login");
    }

    private MachineProfile profileOf(String yaml) throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/machine.yaml"), yaml);
        return new MachineProfile(new EdiHome(home));
    }
}
