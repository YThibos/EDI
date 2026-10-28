package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.testing.StandInScripts;

@Tag("adapter")
class MiseTrustTest {

    @TempDir
    private Path directory;

    @Test
    void trustsACloneWhoseEveryConfigurationMiseTrusts() throws IOException {
        assertThat(miseShowing("~/projects: trusted\n~/projects/harness: trusted\n").trusts(projectClone())).isTrue();
    }

    @Test
    void doesNotTrustACloneWithAnUntrustedConfigurationUnderATrustedParent() throws IOException {
        assertThat(miseShowing("~/projects: trusted\n~/projects/harness: untrusted\n").trusts(projectClone())).isFalse();
    }

    @Test
    void doesNotTrustACloneWithoutATrustedConfiguration() throws IOException {
        assertThat(miseShowing("mise No trusted config files found.\n").trusts(projectClone())).isFalse();
    }

    @Test
    void doesNotTrustACloneWhenMiseFailsOrIsMissing() throws IOException {
        Path failing = StandInScripts.installed(directory.resolve("failing-mise"), "#!/bin/sh\necho '~/projects/harness: trusted'\nexit 1\n");

        assertThat(new MiseTrust(new ExternalPrograms(), failing.toString()).trusts(projectClone())).isFalse();
        assertThat(new MiseTrust(new ExternalPrograms(), directory.resolve("no-mise").toString()).trusts(projectClone())).isFalse();
    }

    private MiseTrust miseShowing(String shown) throws IOException {
        Path mise = StandInScripts.installed(directory.resolve("mise"), "#!/bin/sh\n[ \"$*\" = 'trust --show' ] && printf '%s' '" + shown + "'\n");
        return new MiseTrust(new ExternalPrograms(), mise.toString());
    }

    private Path projectClone() throws IOException {
        return Files.createDirectories(directory.resolve("projects/harness"));
    }
}
