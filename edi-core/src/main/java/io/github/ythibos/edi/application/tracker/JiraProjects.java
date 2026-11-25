package io.github.ythibos.edi.application.tracker;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

@Component
public class JiraProjects {

    private final ProjectRegistry registry;

    public JiraProjects(ProjectRegistry registry) {
        this.registry = registry;
    }

    ProjectEntry keyedBy(ProjectKey workKeys) {
        return registry.entries().stream()
                .filter(project -> workKeys.equals(project.project().workKeys()))
                .filter(JiraProjects::isOnJira)
                .findFirst()
                .orElseThrow(() -> new Refusal(Rule.TRACKER_CONFIGURED, "No registered project books its work on JIRA keys " + workKeys + "-<n>."));
    }

    ProjectEntry named(ProjectName name) {
        ProjectEntry project = registry.entryOf(name);
        if (!isOnJira(project)) {
            throw new Refusal(Rule.TRACKER_CONFIGURED, name.value() + " books its work on " + project.tracker().word() + ", not on JIRA.");
        }
        return project;
    }

    private static boolean isOnJira(ProjectEntry project) {
        return project.tracker() == TrackerKind.JIRA;
    }
}
