package io.github.ythibos.edi.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DenyListTest {

    private static final String COMMIT_ID = "0123456789abcdef";

    @Test
    void readsOneTermPerLineAndSkipsBlankLinesAndComments() {
        DenyList denyList = DenyList.parse("""
                # employer hosts
                  intranet.example \s

                jdoe
                """);

        assertThat(denyList.terms()).containsExactly(new DeniedTerm(2, "intranet.example"), new DeniedTerm(4, "jdoe"));
    }

    @Test
    void findsATermWhateverItsCaseInEveryPartOfACommit() {
        DenyList denyList = DenyList.parse("Intranet.Example\n");
        OutgoingCommit commit = new OutgoingCommit(
                COMMIT_ID,
                "J Doe <j@INTRANET.example>",
                "J Doe <j@intranet.example>",
                "Use the intranet.example mirror",
                List.of("docs/intranet.example/mirror.md", "docs/readme.md"),
                List.of(new AddedLine("docs/readme.md", 3, "url: https://intranet.EXAMPLE/repo")));

        List<PublishFinding> findings = denyList.findIn(new OutgoingChanges(List.of(commit)));

        assertThat(findings).extracting(PublishFinding::location).containsExactly(
                "author of commit 0123456",
                "committer of commit 0123456",
                "message of commit 0123456",
                "docs/intranet.example/mirror.md in commit 0123456",
                "docs/readme.md:3 in commit 0123456");
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.rule()).isEqualTo(PublishRule.DENIED_TERM);
            assertThat(finding.reason()).isEqualTo("deny-list entry 1");
        });
    }

    @Test
    void sparesOnlyTheTermsEqualToAProjectsOwnNamesWhateverTheirCase() {
        DenyList denyList = DenyList.parse("""
                forge.intranet.example
                keeper
                intranet.example
                keeper-two
                """);

        DenyList spared = denyList.sparing(new OwnNames(Set.of("Forge.Intranet.Example", "KEEPER")));

        assertThat(spared.terms()).containsExactly(new DeniedTerm(3, "intranet.example"), new DeniedTerm(4, "keeper-two"));
        assertThat(denyList.sparing(OwnNames.none())).isEqualTo(denyList);
    }

    @Test
    void findsNothingWhenTheListIsEmpty() {
        OutgoingCommit commit = new OutgoingCommit(COMMIT_ID, "a <a@b.c>", "a <a@b.c>", "message", List.of(), List.of());

        assertThat(DenyList.empty().findIn(new OutgoingChanges(List.of(commit)))).isEmpty();
    }
}
