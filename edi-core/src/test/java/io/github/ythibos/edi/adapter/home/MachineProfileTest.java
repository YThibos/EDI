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

    private MachineProfile profileOf(String yaml) throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/machine.yaml"), yaml);
        return new MachineProfile(new EdiHome(home));
    }
}
