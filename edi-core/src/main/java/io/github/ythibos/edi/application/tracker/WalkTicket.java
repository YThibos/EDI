package io.github.ythibos.edi.application.tracker;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.tracker.TicketWalk;

@Component
public class WalkTicket {

    private final JiraProjects projects;
    private final Trackers trackers;

    public WalkTicket(JiraProjects projects, Trackers trackers) {
        this.projects = projects;
        this.trackers = trackers;
    }

    public TicketWalk.Arrived walk(WorkKey key, String state) {
        ProjectEntry project = projects.keyedBy(key.project());
        return arrivedOrRefused(key, state, trackers.serving(project).walk(key, state));
    }

    private static TicketWalk.Arrived arrivedOrRefused(WorkKey key, String state, TicketWalk walk) {
        return switch (walk) {
            case TicketWalk.Arrived arrived -> arrived;
            case TicketWalk.Stopped stopped -> throw new Refusal(Rule.TRACKER_ANSWERED, key + " stopped " + progressOf(stopped) + " on its way to " + state
                    + ": " + stopped.because() + ".");
        };
    }

    private static String progressOf(TicketWalk.Stopped stopped) {
        return stopped.through().isEmpty()
                ? "at " + stopped.from() + " before taking a step"
                : "at " + stopped.lastStatus() + " after the steps " + stopped.from() + " -> " + String.join(" -> ", stopped.through());
    }
}
