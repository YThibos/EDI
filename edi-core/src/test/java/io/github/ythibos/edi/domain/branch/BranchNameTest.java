package io.github.ythibos.edi.domain.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.ythibos.edi.domain.project.WorkKey;

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

    @Test
    void numbersTheBranchOfAnotherTaskOnTheSameKindAndKeyAsTheNextFollowUp() {
        WorkKey key = WorkKey.parse("JUSTMD-36");
        List<BranchName> branchesOfOtherTasks = List.of(BranchName.parse("chore/JUSTMD-36"), BranchName.parse("chore/JUSTMD-36-1"),
                BranchName.parse("research/JUSTMD-36-4"), BranchName.parse("chore/JUSTMD-37-6"));

        assertThat(BranchName.forWorkBeside(BranchKind.CHORE, key, branchesOfOtherTasks)).hasToString("chore/JUSTMD-36-2");
        assertThat(BranchName.forWorkBeside(BranchKind.FEATURE, key, branchesOfOtherTasks)).hasToString("feature/JUSTMD-36");
        assertThat(BranchName.forWorkBeside(BranchKind.CHORE, key, List.of())).hasToString("chore/JUSTMD-36");
    }
}
