package io.github.ythibos.edi.domain.event;

import java.util.Set;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.event.DomainEvent.BoardArmed;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardRoundPosted;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveredToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeAppliedToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeNotApplied;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRaised;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRoundClosed;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftOpened;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeAccepted;
import io.github.ythibos.edi.domain.event.DomainEvent.PipelineRunFinished;

public final class ReachesTheCaptain {

    public static final Set<Class<? extends DomainEvent>> EVENTS = Set.of(DecisionRaised.class, DecisionRoundClosed.class, DraftRecorded.class,
            DraftOpened.class, BranchDeliveredToCaptain.class, ChangeAppliedToCaptain.class, ChangeNotApplied.class, MechanicalChangeAccepted.class,
            PipelineRunFinished.class, BoardArmed.class, BoardRoundPosted.class);

    private ReachesTheCaptain() {
    }

    public static Set<String> typeNames() {
        return EVENTS.stream().map(Class::getSimpleName).collect(Collectors.toUnmodifiableSet());
    }
}
