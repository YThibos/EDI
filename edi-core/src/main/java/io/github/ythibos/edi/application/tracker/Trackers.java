package io.github.ythibos.edi.application.tracker;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TrackedProject;

@Component
public class Trackers {

    private final List<Tracker> trackers;
    private final MachineProfile machine;

    public Trackers(List<Tracker> trackers, MachineProfile machine) {
        this.trackers = List.copyOf(trackers);
        this.machine = machine;
    }

    public Tracker serving(ProjectEntry project) {
        requireReachable(project);
        return trackers.stream()
                .filter(tracker -> tracker.kind() == project.tracker())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No adapter serves " + project.tracker().word() + " trackers."));
    }

    public Optional<String> browseAddressOf(ProjectEntry project, WorkKey key) {
        if (project.tracker().isExternal() && machine.tracker().kind() != project.tracker()) {
            return Optional.empty();
        }
        return trackers.stream().filter(tracker -> tracker.kind() == project.tracker()).findFirst().flatMap(tracker -> tracker.browseAddressOf(key));
    }

    public TrackedProject tracked(ProjectEntry project) {
        return new TrackedProject(project.project().name(), project.tracker(), project.project().workKeys(), machine.tracker().evergreen());
    }

    public String usualIssueType(ProjectEntry project) {
        return project.tracker().isExternal() ? machine.tracker().issueType() : "";
    }

    public void requireEpicsCanBeSet() {
        machine.tracker().requireEpicLinkField();
    }

    public String mergedStatus() {
        return machine.tracker().mergedStatus();
    }

    public boolean hasLanded(Ticket ticket) {
        return machine.tracker().isJira() && machine.tracker().workflow().hasReached(ticket.status(), mergedStatus());
    }

    private void requireReachable(ProjectEntry project) {
        if (project.tracker().isExternal() && machine.tracker().kind() != project.tracker()) {
            throw new Refusal(project.project().name() + " books its work on " + project.tracker().word() + ", but this machine's profile names no "
                    + project.tracker().word() + " tracker; register the project with EDI-native keys on this machine.");
        }
    }
}
