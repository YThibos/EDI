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
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;

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

    private ProjectRegistry registry() {
        return new ProjectRegistry(new EdiHome(home));
    }

    private void register(String yaml) throws IOException {
        Path file = new EdiHome(home).projectRegistry();
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml);
    }
}
