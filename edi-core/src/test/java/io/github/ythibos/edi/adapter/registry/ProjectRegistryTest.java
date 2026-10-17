package io.github.ythibos.edi.adapter.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

@Tag("adapter")
class ProjectRegistryTest {

    @TempDir
    private Path home;

    @Test
    void readsEachProjectWithItsWorkKeysAndLeavesTheRestToLaterReaders() throws IOException {
        register("""
                projects:
                  - name: harness
                    clone: ~/.edi/projects/harness
                    forge: { kind: github, path: someone/harness, account: someone }
                    tracker: { kind: edi, projectKey: HRN }
                    craftRules: true
                  - name: garden
                    delivery: local-branch
                """);

        assertThat(registry().projects()).containsExactly(
                new Project(new ProjectName("harness"), new ProjectKey("HRN")),
                new Project(new ProjectName("garden"), null));
    }

    @Test
    void registersNothingWithoutARegistry() throws IOException {
        assertThat(registry().projects()).isEmpty();

        register("  \n");

        assertThat(registry().projects()).isEmpty();
    }

    @Test
    void refusesARegistryItCannotRead() throws IOException {
        register("projects: [ unclosed");

        assertThatThrownBy(() -> registry().projects()).isInstanceOf(UnreadableRegistry.class).hasMessageContaining("projects.yaml cannot be read");
    }

    @Test
    void refusesAProjectNameEdiCannotUse() throws IOException {
        register("projects:\n  - name: two words\n");

        assertThatThrownBy(() -> registry().projects()).isInstanceOf(UnreadableRegistry.class).hasMessageContaining("two words");
    }

    @Test
    void readsTheForgeEachProjectIsBoundTo() throws IOException {
        register("""
                projects:
                  - name: service
                    forge: { kind: gitlab, host: forge.example.org, path: team/service, account: example-user }
                  - name: tool
                    forge: { kind: github, path: example-owner/tool, account: example-owner }
                  - name: notes
                    delivery: local-branch
                """);

        assertThat(registry().entryOf(new ProjectName("service")).forge())
                .isEqualTo(new RegisteredForge(new ForgeRepository(ForgeKind.GITLAB, "forge.example.org", "team/service"), "example-user"));
        assertThat(registry().entryOf(new ProjectName("tool")).forge().repository().url()).isEqualTo("https://github.com/example-owner/tool");
        assertThat(registry().entryOf(new ProjectName("notes")).forge()).isNull();
        assertThatThrownBy(() -> registry().entryOf(new ProjectName("notes")).requireForge())
                .isInstanceOf(Refusal.class).hasMessageContaining("notes registers no forge");
    }

    @Test
    void refusesLocalApplyAsAProjectsDefaultAndAForgeWithoutItsAccount() throws IOException {
        register("projects:\n  - name: service\n    delivery: local-apply\n");
        assertThatThrownBy(() -> registry().entryOf(new ProjectName("service"))).isInstanceOf(UnreadableRegistry.class)
                .hasMessageContaining("never a project's default");

        register("projects:\n  - name: service\n    forge: { kind: github, path: example-owner/service }\n");
        assertThatThrownBy(() -> registry().entryOf(new ProjectName("service"))).isInstanceOf(UnreadableRegistry.class)
                .hasMessageContaining("registered with the account every forge call for it runs under");
    }

    @Test
    void readsTheTrackerEachProjectBooksItsWorkOn() throws IOException {
        register("""
                projects:
                  - name: service
                    tracker: { kind: jira, projectKey: SVC }
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                  - name: notes
                    delivery: local-branch
                """);

        assertThat(registry().entryOf(new ProjectName("service")).tracker()).isEqualTo(TrackerKind.JIRA);
        assertThat(registry().entryOf(new ProjectName("harness")).tracker()).isEqualTo(TrackerKind.EDI);
        assertThat(registry().entryOf(new ProjectName("notes")).tracker()).isEqualTo(TrackerKind.NONE);

        register("projects:\n  - name: service\n    tracker: { projectKey: SVC }\n");
        assertThatThrownBy(() -> registry().entryOf(new ProjectName("service"))).isInstanceOf(UnreadableRegistry.class)
                .hasMessageContaining("A project's tracker names its kind: jira, edi or none.");
    }

    private ProjectRegistry registry() {
        return new ProjectRegistry(new EdiHome(home));
    }

    private void register(String yaml) throws IOException {
        Path file = new EdiHome(home).projectRegistry();
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml);
    }
}
