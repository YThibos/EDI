package io.github.ythibos.edi.adapter.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.worker.BootId;

class MachineBootTest {

    @TempDir
    private Path directory;

    @Test
    void namesTheMachinesBootByTheIdTheKernelGivesIt() throws IOException {
        Path bootId = Files.writeString(directory.resolve("boot_id"), "6f1c2a3e-1111-4222-8333-944455556666\n");

        assertThat(new MachineBoot(bootId).current()).isEqualTo(new BootId("6f1c2a3e-1111-4222-8333-944455556666"));
    }

    @Test
    void refusesToStartWithoutABootId() {
        assertThatThrownBy(() -> new MachineBoot(directory.resolve("absent")))
                .isInstanceOf(UncheckedIOException.class).hasMessageContaining("tells a machine restart by its boot id");
    }
}
