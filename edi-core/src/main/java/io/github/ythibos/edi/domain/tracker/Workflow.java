package io.github.ythibos.edi.domain.tracker;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public record Workflow(List<String> forward) {

    public Workflow {
        forward = forward.stream().map(status -> Objects.requireNonNullElse(status, "").strip()).toList();
        if (forward.isEmpty() || forward.contains("")) {
            throw new IllegalArgumentException("A workflow lists the statuses a ticket moves through, in their forward order.");
        }
        if (new HashSet<>(forward.stream().map(Workflow::comparable).toList()).size() < forward.size()) {
            throw new IllegalArgumentException("A workflow names each status once: " + forward);
        }
    }

    public void requireStatus(String status) {
        if (positionOf(status) < 0) {
            throw new IllegalArgumentException(status + " is not a status of the workflow " + forward + ".");
        }
    }

    public boolean hasReached(String status, String target) {
        int now = positionOf(status);
        return now >= 0 && now >= positionOf(target);
    }

    public WorkflowStep stepToward(String target, String current, List<AvailableTransition> available) {
        requireStatus(target);
        int goal = positionOf(target);
        int now = positionOf(current);
        if (now == goal) {
            return new WorkflowStep.Arrived();
        }
        if (now < 0) {
            return new WorkflowStep.Stuck("its status " + current + " is not in the workflow " + forward);
        }
        if (now > goal) {
            return new WorkflowStep.AlreadyBeyond(current);
        }
        return available.stream()
                .filter(transition -> isForwardWithin(positionOf(transition.to()), now, goal))
                .max(Comparator.comparingInt(transition -> positionOf(transition.to())))
                .<WorkflowStep>map(WorkflowStep.Take::new)
                .orElseGet(() -> new WorkflowStep.Stuck("no transition from " + current + " moves it toward " + target
                        + "; the tracker offers " + offered(available)));
    }

    private static boolean isForwardWithin(int position, int now, int goal) {
        return position > now && position <= goal;
    }

    private int positionOf(String status) {
        String wanted = comparable(status);
        for (int position = 0; position < forward.size(); position++) {
            if (comparable(forward.get(position)).equals(wanted)) {
                return position;
            }
        }
        return -1;
    }

    private static String offered(List<AvailableTransition> available) {
        return available.isEmpty()
                ? "no transition at all"
                : available.stream().map(transition -> transition.name() + " to " + transition.to()).collect(Collectors.joining(", "));
    }

    private static String comparable(String status) {
        return Objects.requireNonNullElse(status, "").strip().toLowerCase(Locale.ROOT);
    }
}
