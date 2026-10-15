package io.github.ythibos.edi.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PersonalPathsTest {

    @Test
    void refusesThePersonalLayerAtTheRootOfTheRepository() {
        List<PublishFinding> findings = PersonalPaths.personalLayer()
                .findIn(commitTouching("personal/skills/mine.md", "src/personal/allowed.txt", "personalities.md"));

        assertThat(findings).containsExactly(new PublishFinding(
                PublishRule.PERSONAL_PATH, "personal/skills/mine.md in commit fedcba9", "inside the personal layer"));
    }

    @Test
    void refusesAnEdiHomeThatLivesInsideTheRepository() {
        List<PublishFinding> findings = PersonalPaths.personalLayer()
                .andEdiHomeAt("home/.edi/")
                .findIn(commitTouching("home/.edi/config/machine.yaml", "home/.editorconfig"));

        assertThat(findings).containsExactly(new PublishFinding(
                PublishRule.PERSONAL_PATH, "home/.edi/config/machine.yaml in commit fedcba9", "inside the EDI home"));
    }

    private static OutgoingChanges commitTouching(String... paths) {
        return new OutgoingChanges(List.of(
                new OutgoingCommit("fedcba9876543210", "a <a@b.c>", "a <a@b.c>", "message", List.of(paths), List.of())));
    }
}
