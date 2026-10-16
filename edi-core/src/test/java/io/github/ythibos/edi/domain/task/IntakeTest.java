package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.project.ProjectName;

class IntakeTest {

    private static final List<ProjectName> HARNESS = List.of(new ProjectName("harness"));

    @Test
    void needsATitle() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Intake(TaskKind.SHIP, "  ", HARNESS, null, null, List.of()));
    }

    @Test
    void namesEachProjectAndBlockerOnce() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Intake(TaskKind.SHIP, "Fit", List.of(new ProjectName("a"), new ProjectName("a")), null, null, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Intake(TaskKind.SHIP, "Fit", HARNESS, null, null, List.of(new TaskId("x"), new TaskId("x"))));
    }

    @Test
    void knowsWhetherItNamesTheProjectItWorksOn() {
        assertThat(new Intake(TaskKind.SHIP, "Fit", HARNESS, null, null, List.of()).namesTheProjectItWorksOn()).isTrue();
        assertThat(new Intake(TaskKind.SCOUT, "Look", List.of(), null, null, List.of()).namesTheProjectItWorksOn()).isFalse();
        assertThat(new Intake(TaskKind.CAPTAIN, "Choose", List.of(), null, null, List.of()).namesTheProjectItWorksOn()).isTrue();
    }

    @Test
    void readsKindsStatesAndHoldsByTheirWords() {
        assertThat(TaskKind.named("scout")).isEqualTo(TaskKind.SCOUT);
        assertThat(TaskState.named("in-flight")).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(HoldKind.named("approval")).isEqualTo(HoldKind.APPROVAL);
        assertThatIllegalArgumentException().isThrownBy(() -> TaskKind.named("chore")).withMessage("A task kind is ship, scout or captain, not chore.");
        assertThatIllegalArgumentException().isThrownBy(() -> TaskState.named("in_flight"));
    }

    @Test
    void takesABaseBranchName() {
        assertThat(new BaseBranch("feature/EDI-2").name()).isEqualTo("feature/EDI-2");
        assertThatIllegalArgumentException().isThrownBy(() -> new BaseBranch("--force"));
        assertThatIllegalArgumentException().isThrownBy(() -> new BaseBranch("main..other"));
        assertThatIllegalArgumentException().isThrownBy(() -> new BaseBranch("two words"));
    }
}
