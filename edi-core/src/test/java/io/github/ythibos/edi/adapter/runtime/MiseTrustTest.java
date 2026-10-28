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
    private Path home;

    @Test
    void trustsACloneWhoseOwnConfigurationsMiseTrustsWhateverItsParentsTrust() throws IOException {
        String shown = "~/projects: untrusted\n~/projects/harness: trusted\n" + home.resolve("projects/harness/tools") + ": trusted\n";

        assertThat(miseShowing(shown).trusts(projectClone())).isTrue();
    }

    @Test
    void doesNotTrustACloneWithAnUntrustedConfigurationOfItsOwn() throws IOException {
        assertThat(miseShowing("~/projects: trusted\n~/projects/harness: trusted\n~/projects/harness/tools: untrusted\n").trusts(projectClone())).isFalse();
    }

    @Test
    void doesNotTrustACloneWithoutAConfigurationOfItsOwnUnderATrustedParent() throws IOException {
        assertThat(miseShowing("~/projects: trusted\n~/projects/harness-lamp: trusted\n").trusts(projectClone())).isFalse();
    }

    @Test
    void doesNotTrustACloneWhereMiseFindsNoConfiguration() throws IOException {
        Path mise = StandInScripts.installed(home.resolve("quiet-mise"), "#!/bin/sh\necho 'mise No trusted config files found.' >&2\n");

        assertThat(new MiseTrust(new ExternalPrograms(), mise.toString(), home).trusts(projectClone())).isFalse();
    }

    @Test
    void doesNotTrustACloneWhenMiseFailsOrIsMissing() throws IOException {
        Path failing = StandInScripts.installed(home.resolve("failing-mise"), "#!/bin/sh\necho '~/projects/harness: trusted'\nexit 1\n");

        assertThat(new MiseTrust(new ExternalPrograms(), failing.toString(), home).trusts(projectClone())).isFalse();
        assertThat(new MiseTrust(new ExternalPrograms(), home.resolve("no-mise").toString(), home).trusts(projectClone())).isFalse();
    }

    private MiseTrust miseShowing(String shown) throws IOException {
        Path mise = StandInScripts.installed(home.resolve("mise"), "#!/bin/sh\n[ \"$*\" = 'trust --show' ] && printf '%s' '" + shown + "'\n");
        return new MiseTrust(new ExternalPrograms(), mise.toString(), home);
    }

    private Path projectClone() throws IOException {
        return Files.createDirectories(home.resolve("projects/harness"));
    }
}
