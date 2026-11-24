package io.github.ythibos.edi.adapter.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.adapter.home.TrackerSettings;
import io.github.ythibos.edi.adapter.store.WorkflowCache;
import io.github.ythibos.edi.adapter.tracker.JiraTransitions.Standing;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.AvailableTransition;
import io.github.ythibos.edi.domain.tracker.TicketWalk;
import io.github.ythibos.edi.domain.tracker.Workflow;
import io.github.ythibos.edi.domain.tracker.WorkflowGraph;

final class JiraWalk {

    private static final int MOST_STEPS_PER_STATUS = 2;

    private final JiraTransitions transitions;
    private final WorkflowCache cache;

    JiraWalk(JiraTransitions transitions, WorkflowCache cache) {
        this.transitions = transitions;
        this.cache = cache;
    }

    TicketWalk walk(TrackerSettings settings, WorkKey key, String target) {
        Workflow workflow = settings.workflow();
        workflow.requireStatus(target);
        Standing start = transitions.standingOf(settings, key);
        List<String> through = new ArrayList<>();
        try {
            return walked(new Walk(settings, key, target, start, through));
        } catch (Refusal | TrackerUnavailable failed) {
            return new TicketWalk.Stopped(start.status(), through, failed.getMessage());
        }
    }

    private TicketWalk walked(Walk walk) {
        String status = walk.start().status();
        int mostSteps = walk.settings().workflow().forward().size() * MOST_STEPS_PER_STATUS;
        for (int taken = 0; taken <= mostSteps; taken++) {
            List<AvailableTransition> offered = transitions.offeredTo(walk.settings(), walk.key());
            WorkflowGraph graph = learned(walk, status, offered);
            if (status.equalsIgnoreCase(walk.target())) {
                return new TicketWalk.Arrived(walk.start().status(), walk.through());
            }
            Optional<String> next = nextStatus(walk, graph, status);
            if (next.isEmpty()) {
                return new TicketWalk.Stopped(walk.start().status(), walk.through(), noWayOn(graph, status, walk.target()));
            }
            status = transitions.statusAfter(walk.settings(), walk.key(), transitionTo(offered, next.get()));
            walk.through().add(status);
        }
        return new TicketWalk.Stopped(walk.start().status(), walk.through(), "it took more than " + mostSteps + " steps without arriving at " + walk.target());
    }

    private WorkflowGraph learned(Walk walk, String status, List<AvailableTransition> offered) {
        List<String> offeredTo = offered.stream().map(AvailableTransition::to).toList();
        cache.remember(walk.key().project(), walk.start().issueType(), status, offeredTo);
        return cache.graphOf(walk.key().project(), walk.start().issueType());
    }

    private Optional<String> nextStatus(Walk walk, WorkflowGraph graph, String status) {
        Workflow workflow = walk.settings().workflow();
        return graph.pathBetween(status, walk.target())
                .or(() -> graph.pathToNearestUnseen(status, unseen -> workflow.separation(unseen, walk.target())))
                .map(List::getFirst);
    }

    private static AvailableTransition transitionTo(List<AvailableTransition> offered, String status) {
        return offered.stream()
                .filter(transition -> transition.to().equalsIgnoreCase(status))
                .findFirst()
                .orElseThrow();
    }

    private static String noWayOn(WorkflowGraph graph, String status, String target) {
        return "no transition path from " + status + " to " + target + " is known, and no unexplored status is reachable; the statuses seen are "
                + String.join(", ", graph.seenStatuses());
    }

    private record Walk(TrackerSettings settings, WorkKey key, String target, Standing start, List<String> through) {
    }
}
