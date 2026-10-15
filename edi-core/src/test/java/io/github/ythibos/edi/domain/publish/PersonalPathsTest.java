package io.github.ythibos.edi.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PersonalPathsTest {

    private static final String COMMIT_ID = "fedcba9876543210";

    @Test
    void refusesThePersonalLayerAtTheRootOfTheRepository() {
        List<PublishFinding> findings = PersonalPaths.personalLayer()
                .findIn(commitTouching("personal/skills/mine.md", "src/personal/allowed.txt", "personalities.md"));

        assertThat(findings).containsExactly(PublishFinding.onPath(
                PublishRule.PERSONAL_PATH, "personal/skills/mine.md", COMMIT_ID, "inside the personal layer"));
    }

    @Test
    void refusesAnEdiHomeThatLivesInsideTheRepository() {
        List<PublishFinding> findings = PersonalPaths.personalLayer()
                .andEdiHomeAt("home/.edi/")
                .findIn(commitTouching("home/.edi/config/machine.yaml", "home/.editorconfig"));

        assertThat(findings).containsExactly(PublishFinding.onPath(
                PublishRule.PERSONAL_PATH, "home/.edi/config/machine.yaml", COMMIT_ID, "inside the EDI home"));
    }

    private static OutgoingChanges commitTouching(String... paths) {
        return new OutgoingChanges(List.of(
                new OutgoingCommit(COMMIT_ID, "a <a@b.c>", "a <a@b.c>", "message", List.of(paths), List.of())));
    }
}
