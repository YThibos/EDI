package io.github.ythibos.edi.domain.tracker;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.project.WorkKey;

public record DriftRules(
        Workflow workflow,
        String mergedStatus,
        List<String> reviewStatuses,
        List<String> implementedStatuses,
        String doneStatus,
        Set<WorkKey> evergreen) {

    private static final List<String> TYPES_THAT_NEVER_SIT_IN_DONE_BEFORE_PRODUCTION = List.of("Story", "Bug");

    public DriftRules {
        reviewStatuses = List.copyOf(reviewStatuses);
        implementedStatuses = List.copyOf(implementedStatuses);
        doneStatus = Objects.requireNonNullElse(doneStatus, "").strip();
        evergreen = Set.copyOf(evergreen);
    }

    public DriftReport report(String project, List<ProjectTicket> tickets, RepositorySightings sightings) {
        List<TicketDrift> drifts = tickets.stream()
                .filter(ticket -> !evergreen.contains(ticket.ticket().key()))
                .flatMap(ticket -> driftsOf(ticket, sightings))
                .toList();
        return new DriftReport(project, drifts, unchecked(sightings));
    }

    private Stream<TicketDrift> driftsOf(ProjectTicket ticket, RepositorySightings sightings) {
        return Stream.of(DriftKind.values())
                .filter(kind -> drifts(kind, ticket, sightings))
                .map(kind -> new TicketDrift(ticket.ticket().key(), ticket.ticket().status(), kind));
    }

    private boolean drifts(DriftKind kind, ProjectTicket ticket, RepositorySightings sightings) {
        WorkKey key = ticket.ticket().key();
        String status = ticket.ticket().status();
        return switch (kind) {
            case MERGED_BEFORE_ITS_MERGED_STATE -> sightings.landedOnTheBase().contains(key) && isBeforeTheMergedStatus(status);
            case DONE_BEFORE_PRODUCTION -> isInDoneAsAStoryOrBug(ticket) && sightings.reachedProduction().isAvailable()
                    && !sightings.reachedProduction().includes(key);
            case IN_REVIEW_WITHOUT_AN_OPEN_REQUEST -> isIn(reviewStatuses, status) && sightings.underOpenRequest().isAvailable()
                    && !sightings.underOpenRequest().includes(key);
            case IMPLEMENTED_WITHOUT_A_COMMIT -> isIn(implementedStatuses, status) && !sightings.landedOnTheBase().contains(key);
        };
    }

    private boolean isBeforeTheMergedStatus(String status) {
        return workflow.separation(status, mergedStatus) != Integer.MAX_VALUE && !workflow.hasReached(status, mergedStatus);
    }

    private boolean isInDoneAsAStoryOrBug(ProjectTicket ticket) {
        return isIn(List.of(doneStatus), ticket.ticket().status())
                && TYPES_THAT_NEVER_SIT_IN_DONE_BEFORE_PRODUCTION.stream().anyMatch(ticket::isOfType);
    }

    private List<String> unchecked(RepositorySightings sightings) {
        return Stream.of(
                        notChecked(DriftKind.IMPLEMENTED_WITHOUT_A_COMMIT, namesNone(implementedStatuses.isEmpty(), "implementedStatuses")),
                        notChecked(DriftKind.DONE_BEFORE_PRODUCTION,
                                namesNone(doneStatus.isEmpty(), "doneStatus").or(() -> Optional.ofNullable(sightings.reachedProduction().unavailableBecause()))),
                        notChecked(DriftKind.IN_REVIEW_WITHOUT_AN_OPEN_REQUEST,
                                namesNone(reviewStatuses.isEmpty(), "reviewStatuses").or(() -> Optional.ofNullable(sightings.underOpenRequest().unavailableBecause()))))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<String> namesNone(boolean missing, String setting) {
        return missing ? Optional.of("the machine profile names no tracker." + setting) : Optional.empty();
    }

    private static Optional<String> notChecked(DriftKind kind, Optional<String> because) {
        return because.map(reason -> kind.sentence() + ": not checked, because " + reason);
    }

    private static boolean isIn(List<String> statuses, String status) {
        return !status.isEmpty() && statuses.stream().anyMatch(listed -> listed.equalsIgnoreCase(status));
    }
}
