package io.github.ythibos.edi.domain.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record PlanDraft(PlanHeading heading, List<SliceDraft> slices) {

    public PlanDraft {
        if (slices.isEmpty()) {
            throw new IllegalArgumentException("The plan " + heading.key() + " has at least one slice.");
        }
        slices = withEveryWaitNamed(inOrder(slices));
        requireNumberedOnce(slices);
        requireOwnKeys(slices);
        requireWaitsOnEarlierSlices(slices);
    }

    public Plan planned(List<TaskId> tasks) {
        if (tasks.size() != slices.size()) {
            throw new IllegalArgumentException("Each of the " + slices.size() + " slices of " + heading.key() + " is one task.");
        }
        return new Plan(heading, IntStream.range(0, slices.size()).mapToObj(index -> slices.get(index).plannedAs(tasks.get(index))).toList());
    }

    private static List<SliceDraft> inOrder(List<SliceDraft> slices) {
        return slices.stream().sorted(Comparator.comparingInt(SliceDraft::number)).toList();
    }

    private static List<SliceDraft> withEveryWaitNamed(List<SliceDraft> slices) {
        List<SliceDraft> named = new ArrayList<>();
        Optional<Integer> sliceBefore = Optional.empty();
        for (SliceDraft slice : slices) {
            named.add(slice.namesItsWait() ? slice : slice.waitingFor(SliceWait.onTheSliceBefore(sliceBefore)));
            sliceBefore = Optional.of(slice.number());
        }
        return List.copyOf(named);
    }

    private static void requireNumberedOnce(List<SliceDraft> slices) {
        Set<Integer> numbers = new HashSet<>();
        slices.stream()
                .filter(slice -> !numbers.add(slice.number()))
                .findFirst()
                .ifPresent(twice -> {
                    throw new IllegalArgumentException("The plan numbers slice " + twice.number() + " twice.");
                });
    }

    private static void requireOwnKeys(List<SliceDraft> slices) {
        Set<WorkKey> keys = new HashSet<>();
        slices.stream()
                .filter(slice -> !keys.add(slice.key()))
                .findFirst()
                .ifPresent(shared -> {
                    throw new IllegalArgumentException("Slice " + shared.number() + " shares the key " + shared.key()
                            + " with an earlier slice; each slice has its own key, because tasks that share a key share a branch.");
                });
    }

    private static void requireWaitsOnEarlierSlices(List<SliceDraft> slices) {
        Set<Integer> earlier = new HashSet<>();
        for (SliceDraft slice : slices) {
            if (slice.awaits() instanceof SliceWait.After after && !earlier.contains(after.slice())) {
                throw new IllegalArgumentException("Slice " + slice.number() + " waits " + after.words()
                        + ", and a slice waits only for an earlier slice of its plan.");
            }
            earlier.add(slice.number());
        }
    }
}
