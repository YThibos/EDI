package io.github.ythibos.edi.domain.tracker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

public final class WorkflowGraph {

    private final Map<String, Set<String>> offeredFrom;

    private WorkflowGraph(Map<String, Set<String>> offeredFrom) {
        this.offeredFrom = offeredFrom;
    }

    public static WorkflowGraph unknown() {
        return new WorkflowGraph(statusMap());
    }

    public static WorkflowGraph of(Map<String, ? extends Collection<String>> offeredFrom) {
        Map<String, Set<String>> graph = statusMap();
        offeredFrom.forEach((status, offered) -> graph.put(status, statusSet(offered)));
        return new WorkflowGraph(graph);
    }

    public WorkflowGraph learning(String status, Collection<String> offeredTo) {
        Map<String, Set<String>> learned = statusMap();
        learned.putAll(offeredFrom);
        learned.put(status, statusSet(offeredTo));
        return new WorkflowGraph(learned);
    }

    public boolean hasSeen(String status) {
        return offeredFrom.containsKey(status);
    }

    public Set<String> seenStatuses() {
        return Collections.unmodifiableSet(offeredFrom.keySet());
    }

    public Set<String> offeredFrom(String status) {
        return Collections.unmodifiableSet(offeredFrom.getOrDefault(status, Set.of()));
    }

    public Optional<List<String>> pathBetween(String from, String to) {
        return pathsFrom(from).stream().filter(path -> path.getLast().equalsIgnoreCase(to)).findFirst();
    }

    public Optional<List<String>> pathToNearestUnseen(String from, ToIntFunction<String> separationFromTheTarget) {
        return pathsFrom(from).stream()
                .filter(path -> !hasSeen(path.getLast()))
                .min(Comparator.<List<String>>comparingInt(List::size)
                        .thenComparingInt(path -> separationFromTheTarget.applyAsInt(path.getLast()))
                        .thenComparing(List::getLast, String.CASE_INSENSITIVE_ORDER));
    }

    private List<List<String>> pathsFrom(String from) {
        Set<String> reached = statusSet(List.of(from));
        Deque<List<String>> frontier = new ArrayDeque<>(List.of(List.of()));
        List<List<String>> paths = new ArrayList<>();
        while (!frontier.isEmpty()) {
            List<String> path = frontier.poll();
            for (String next : offeredFrom(path.isEmpty() ? from : path.getLast())) {
                if (reached.add(next)) {
                    List<String> longer = new ArrayList<>(path);
                    longer.add(next);
                    paths.add(longer);
                    frontier.add(longer);
                }
            }
        }
        return paths;
    }

    private static Map<String, Set<String>> statusMap() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    private static Set<String> statusSet(Collection<String> statuses) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(statuses);
        return set;
    }
}
