package io.github.ythibos.edi.domain.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.regex.Matcher;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BranchNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"feature/EDI-5", "chore/EDI-1", "research/JUSTMD-36", "feature/JUSTMD-36-4"})
    void readsAKeyedBranchBackAsItIsWritten(String branch) {
        assertThat(BranchName.parse(branch)).hasToString(branch);
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "feature/", "feature/EDI", "feature/EDI-x", "hotfix/EDI-5", "feature/edi-5", "feature/EDI-5-1-2", "feature/EDI-5/x"})
    void refusesABranchThatIsNotKindAndKey(String branch) {
        assertThatIllegalArgumentException().isThrownBy(() -> BranchName.parse(branch)).withMessageContaining("feature|chore|research/<KEY>-<n>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fm/EDI-5", "edi/EDI-5", "fm/feature/EDI-5"})
    void refusesTheHarnessPrefixesByName(String branch) {
        assertThatIllegalArgumentException().isThrownBy(() -> BranchName.parse(branch)).withMessageContaining("never starts with");
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature/EDI-5", "chore/EDI-1", "research/JUSTMD-36", "feature/JUSTMD-36-4", "feature/A2B-123456789-12"})
    void capturesTheWorkKeyOfEveryKeyedBranch(String branch) {
        Matcher capture = BranchName.workKeyCapture().matcher(branch);

        assertThat(capture.matches()).isTrue();
        assertThat(capture.group(1)).isEqualTo(BranchName.parse(branch).workKey().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "feature/EDI", "hotfix/EDI-5", "feature/edi-5", "fm/EDI-5", "fm/feature/EDI-5", "feature/EDI-5/x"})
    void capturesNoWorkKeyFromABranchThatIsNotKindAndKey(String branch) {
        assertThat(BranchName.workKeyCapture().matcher(branch).matches()).isFalse();
    }
}
