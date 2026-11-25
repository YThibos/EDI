package io.github.ythibos.edi.application.tracker;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.delivery.Forges;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.tracker.DriftReport;
import io.github.ythibos.edi.domain.tracker.ProjectTicket;
import io.github.ythibos.edi.domain.tracker.RepositorySightings;
import io.github.ythibos.edi.domain.tracker.Sighting;

@Component
public class TicketDrifts {

    private final JiraProjects projects;
    private final Trackers trackers;
    private final Forges forges;
    private final Git git;

    public TicketDrifts(JiraProjects projects, Trackers trackers, Forges forges, Git git) {
        this.projects = projects;
        this.trackers = trackers;
        this.forges = forges;
        this.git = git;
    }

    public DriftReport of(ProjectName name) {
        ProjectEntry project = projects.named(name);
        List<ProjectTicket> tickets = trackers.serving(project).ticketsOf(project.project().workKeys());
        return trackers.driftRules().report(name.value(), tickets, sightingsOf(project));
    }

    private RepositorySightings sightingsOf(ProjectEntry project) {
        git.fetchOrigin(project.projectClone());
        return new RepositorySightings(landedOnTheBase(project), reachedProduction(project), underOpenRequest(project));
    }

    private Set<WorkKey> landedOnTheBase(ProjectEntry project) {
        return keysOn(project.projectClone(), project.requireBase());
    }

    private Sighting reachedProduction(ProjectEntry project) {
        return project.production() == null
                ? Sighting.unavailable("the project's registry entry names no production branch")
                : Sighting.of(keysOn(project.projectClone(), project.production()));
    }

    private Sighting underOpenRequest(ProjectEntry project) {
        if (project.forge() == null) {
            return Sighting.unavailable("the project's registry entry names no forge");
        }
        return Sighting.of(forges.serving(project.forge()).openMergeRequests(project.forge()).stream()
                .map(MergeRequestView::sourceBranch)
                .flatMap(branch -> keyOf(branch).stream())
                .collect(Collectors.toSet()));
    }

    private Set<WorkKey> keysOn(Path clone, BaseBranch branch) {
        return git.workKeysOpeningCommitsOn(clone, "origin/" + branch);
    }

    private static Optional<WorkKey> keyOf(String sourceBranch) {
        return Optional.ofNullable(sourceBranch)
                .map(BranchName.workKeyCapture()::matcher)
                .filter(Matcher::matches)
                .flatMap(matched -> WorkKey.opening(matched.group(1)));
    }
}
