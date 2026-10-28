package io.github.ythibos.edi.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretReadsTest {

    private static final Secrets SECRETS = new Secrets(List.of("/state/core.token", "/tracker-keys.yml"), true);

    @ParameterizedTest
    @ValueSource(strings = {"/home/someone/.edi/state/core.token", "/mnt/c/tmp/tracker-keys.yml"})
    void refusesAReadOfASecretFile(String file) {
        assertThat(SecretReads.refusalForReading(file, SECRETS)).hasValueSatisfying(refusal -> {
            assertThat(refusal.rule()).isEqualTo(GuardRule.SECRET);
            assertThat(refusal.reason()).startsWith("this read touches a secret").contains("(I-32)", "(I-06)");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/repository/edi-core/src/main/java/Lamp.java", "/home/someone/.edi/state/core.port", "/repository/tracker-keys.yml.example"})
    void letsAReadOfAnyOtherFileThrough(String file) {
        assertThat(SecretReads.refusalForReading(file, SECRETS)).isEmpty();
    }
}
