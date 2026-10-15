package io.github.ythibos.edi.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PublishVerdictTest {

    private static final String COMMIT_ID = "0123456789abcdef";
    private static final String DENIED_PATH = "personal/intranet.example/notes.md";
    private static final String CLEAN_PATH = "docs/readme.md";

    @Test
    void namesEveryPathThatCarriesADeniedTermOnlyByItsPositionInTheCommit() {
        DenyList denyList = DenyList.parse("intranet.example\n");
        OutgoingChanges changes = new OutgoingChanges(List.of(
                new OutgoingCommit(COMMIT_ID, "a <a@b.c>", "a <a@b.c>", "message", List.of(CLEAN_PATH, DENIED_PATH), List.of())));
        PublishVerdict verdict = new PublishVerdict(List.of(
                PublishFinding.onPath(PublishRule.DENIED_TERM, DENIED_PATH, COMMIT_ID, "deny-list entry 1"),
                PublishFinding.onLine(PublishRule.DENIED_TERM, DENIED_PATH, 4, COMMIT_ID, "deny-list entry 1"),
                PublishFinding.onLine(PublishRule.SECRET, DENIED_PATH, 7, COMMIT_ID, "gitleaks rule generic-api-key"),
                PublishFinding.onPath(PublishRule.PERSONAL_PATH, DENIED_PATH, COMMIT_ID, "inside the personal layer"),
                PublishFinding.onLine(PublishRule.SECRET, CLEAN_PATH, 2, COMMIT_ID, "gitleaks rule generic-api-key")));

        PublishVerdict hidden = verdict.hidingPathsDeniedBy(denyList, changes);

        assertThat(hidden.findings()).extracting(PublishFinding::toString).containsExactly(
                "path 2 of commit 0123456: deny-list entry 1",
                "line 4 of path 2 of commit 0123456: deny-list entry 1",
                "line 7 of path 2 of commit 0123456: gitleaks rule generic-api-key",
                "path 2 of commit 0123456: inside the personal layer",
                "docs/readme.md:2 in commit 0123456: gitleaks rule generic-api-key");
        assertThat(hidden.allowsPush()).isFalse();
    }
}
