package io.github.ythibos.edi.domain.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class WorkflowTest {

    private static final Workflow WORKFLOW = new Workflow(List.of("To Do", "In Progress", "In Review", "Testing", "Done"));
    private static final AvailableTransition START = new AvailableTransition("11", "Start", "In Progress");
    private static final AvailableTransition REVIEW = new AvailableTransition("21", "Review", "In Review");
    private static final AvailableTransition SHIP = new AvailableTransition("31", "Ship", "Testing");
    private static final AvailableTransition CLOSE = new AvailableTransition("41", "Close", "Done");

    @Test
    void takesTheTransitionThatGoesFurthestWithoutPassingTheTarget() {
        assertThat(WORKFLOW.stepToward("Testing", "To Do", List.of(START, REVIEW, CLOSE))).isEqualTo(new WorkflowStep.Take(REVIEW));
        assertThat(WORKFLOW.stepToward("testing", "in review", List.of(SHIP, CLOSE))).isEqualTo(new WorkflowStep.Take(SHIP));
    }

    @Test
    void stopsAtTheTargetOrBeyondIt() {
        assertThat(WORKFLOW.stepToward("Testing", "Testing", List.of(CLOSE))).isEqualTo(new WorkflowStep.Arrived());
        assertThat(WORKFLOW.stepToward("Testing", "Done", List.of())).isEqualTo(new WorkflowStep.AlreadyBeyond("Done"));
    }

    @Test
    void namesWhyATicketCannotMoveOn() {
        assertThat(WORKFLOW.stepToward("Testing", "In Progress", List.of(CLOSE)))
                .isEqualTo(new WorkflowStep.Stuck("no transition from In Progress moves it toward Testing; the tracker offers Close to Done"));
        assertThat(WORKFLOW.stepToward("Testing", "Blocked", List.of(START)))
                .isEqualTo(new WorkflowStep.Stuck("its status Blocked is not in the workflow [To Do, In Progress, In Review, Testing, Done]"));
    }

    @Test
    void refusesAWorkflowWithoutStatusesOrWithARepeatedOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Workflow(List.of())).withMessageContaining("in their forward order");
        assertThatIllegalArgumentException().isThrownBy(() -> new Workflow(List.of("To Do", "to do"))).withMessageContaining("each status once");
        assertThatIllegalArgumentException().isThrownBy(() -> WORKFLOW.requireStatus("Released")).withMessageContaining("not a status of the workflow");
    }
}
