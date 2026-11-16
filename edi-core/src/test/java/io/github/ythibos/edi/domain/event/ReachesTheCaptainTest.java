package io.github.ythibos.edi.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ReachesTheCaptainTest {

    @Test
    void namesOnlyEventsThatPutSomethingOnTheCaptainsListAndNoMoreThanTheDomainEventsThereAre() {
        Set<String> every = Arrays.stream(DomainEvent.class.getPermittedSubclasses()).map(Class::getSimpleName).collect(Collectors.toSet());

        assertThat(ReachesTheCaptain.typeNames()).isSubsetOf(every).containsExactlyInAnyOrder("DecisionRaised", "DecisionRoundClosed", "DraftRecorded",
                "DraftOpened", "BranchDeliveredToCaptain", "ChangeAppliedToCaptain", "ChangeNotApplied", "MechanicalChangeAccepted", "PipelineRunFinished",
                "BoardArmed", "BoardRoundPosted");
    }

    @Test
    void leavesWhatAWorkerReportsAsWorkAndWhatThePrimarySteersOutOfTheCaptainsList() {
        assertThat(ReachesTheCaptain.typeNames()).doesNotContain("StatusReported", "SteerSent", "SteerAcknowledged", "TaskAdded", "WorkerTurnEnded");
    }
}
