package io.github.ythibos.edi.application.tracker;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDetails;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TrackedProject;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.domain.tracker.WorkKeying;

@Service
public class TaskKeys {

    private final ProjectRegistry registry;
    private final Trackers trackers;

    public TaskKeys(ProjectRegistry registry, Trackers trackers) {
        this.registry = registry;
        this.trackers = trackers;
    }

    public WorkKeying keyingFor(Intake intake, String notes, TicketDetails details) {
        if (intake.kind() == TaskKind.CAPTAIN) {
            requireNoDetails(details);
            return intake.workKey() == null ? new WorkKeying.Unkeyed() : new WorkKeying.Given(Ticket.keyedOnly(intake.workKey()));
        }
        ProjectEntry project = registry.entryOf(intake.projects().getFirst());
        return intake.workKey() == null ? keyingOfUnkeyedWork(project, intake, notes, details) : givenKey(project, intake.workKey(), details);
    }

    public Ticket verified(Intake intake, WorkKey key) {
        if (intake.kind() == TaskKind.CAPTAIN) {
            return Ticket.keyedOnly(key);
        }
        return givenKey(registry.entryOf(intake.projects().getFirst()), key, TicketDetails.NONE).ticket();
    }

    public WorkKey issue(ProjectName project, TicketDraft draft) {
        return trackers.serving(registry.entryOf(project)).create(draft);
    }

    private static void requireNoDetails(TicketDetails details) {
        if (!details.isEmpty()) {
            throw new Refusal(Rule.WORK_KEY, "Ticket details describe a new ticket of an external tracker, so they come only with a task that has no key yet"
                    + " on a project whose tracker asks before it creates one.");
        }
    }

    private WorkKeying keyingOfUnkeyedWork(ProjectEntry project, Intake intake, String notes, TicketDetails details) {
        TrackedProject tracked = trackers.tracked(project);
        if (tracked.tracker() == TrackerKind.NONE) {
            requireNoDetails(details);
            return new WorkKeying.Unkeyed();
        }
        if (tracked.tracker().keysByIssueNumber()) {
            throw new Refusal(Rule.WORK_KEY, project.project().name() + " keys its work by its GitHub issue numbers, so a " + intake.kind().word()
                    + " on it is added with --key " + tracked.key() + "-<the issue's number>, once its issue is open.");
        }
        TicketDraft draft = details.draftFor(tracked.key(), trackers.usualIssueType(project), intake.title(), notes);
        if (!tracked.tracker().isExternal()) {
            requireNoDetails(details);
            return new WorkKeying.ToIssue(draft);
        }
        if (draft.namesAnEpic()) {
            trackers.requireEpicsCanBeSet();
        }
        return new WorkKeying.ToPropose(draft, trackers.serving(project).findExisting(new TicketContext(tracked.key(), intake.title())));
    }

    private WorkKeying.Given givenKey(ProjectEntry project, WorkKey key, TicketDetails details) {
        requireNoDetails(details);
        trackers.tracked(project).requireOwns(key);
        return new WorkKeying.Given(trackers.serving(project).verified(key));
    }
}
