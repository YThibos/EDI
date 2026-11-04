package io.github.ythibos.edi.adapter.home;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.domain.tracker.Workflow;

public record TrackerSettings(
        TrackerKind kind,
        URI baseUrl,
        Credentials credentials,
        Set<WorkKey> evergreen,
        Workflow workflow,
        String mergedStatus,
        String issueType,
        String epicLinkField) {

    private static final String LOOPBACK = "127.0.0.1";
    private static final TrackerSettings NONE = new TrackerSettings(TrackerKind.NONE, null, null, Set.of(), null, null, null, null);

    public TrackerSettings {
        Objects.requireNonNull(kind, "The machine profile names its tracker.");
        evergreen = Set.copyOf(evergreen);
        if (kind == TrackerKind.EDI || kind == TrackerKind.GITHUB) {
            throw new IllegalArgumentException("tracker.kind is jira or none: EDI-native keys and issue-number keys need no tracker on the machine.");
        }
        if (kind == TrackerKind.JIRA) {
            requireAJiraConnection(baseUrl, credentials);
            requireAWorkflowFor(workflow, mergedStatus);
            if (issueType == null || issueType.isBlank()) {
                throw new IllegalArgumentException("tracker.issueType names the issue type of new tickets, such as Story.");
            }
        }
    }

    public static TrackerSettings none() {
        return NONE;
    }

    public boolean isJira() {
        return kind == TrackerKind.JIRA;
    }

    public List<String> identifyingNames() {
        return isJira() ? List.of(baseUrl.getHost(), credentials.keysFile().getFileName().toString()) : List.of();
    }

    public String requireEpicLinkField() {
        if (epicLinkField == null || epicLinkField.isBlank()) {
            throw new Refusal("The machine profile names no tracker.epicLinkField, so a ticket's epic cannot be set.");
        }
        return epicLinkField.strip();
    }

    private static void requireAJiraConnection(URI baseUrl, Credentials credentials) {
        if (baseUrl == null || baseUrl.getHost() == null || !isEncryptedOrLocal(baseUrl)) {
            throw new IllegalArgumentException("tracker.baseUrl is the https address of the JIRA server, such as https://jira.example.org.");
        }
        if (credentials == null) {
            throw new IllegalArgumentException("tracker.credentials names the keys file and its section that hold the JIRA access token.");
        }
    }

    private static boolean isEncryptedOrLocal(URI baseUrl) {
        return "https".equals(baseUrl.getScheme()) || "http".equals(baseUrl.getScheme()) && LOOPBACK.equals(baseUrl.getHost());
    }

    private static void requireAWorkflowFor(Workflow workflow, String mergedStatus) {
        if (workflow == null) {
            throw new IllegalArgumentException("tracker.workflow lists the ticket statuses in their forward order.");
        }
        if (mergedStatus == null || mergedStatus.isBlank()) {
            throw new IllegalArgumentException("tracker.mergedStatus names the status a shipped ticket moves to after its merge.");
        }
        workflow.requireStatus(mergedStatus);
    }

    public record Credentials(Path keysFile, String section) {

        public Credentials {
            if (keysFile == null || !keysFile.isAbsolute()) {
                throw new IllegalArgumentException("tracker.credentials.keysFile is the absolute path of the keys file.");
            }
            if (section == null || section.isBlank()) {
                throw new IllegalArgumentException("tracker.credentials.section names the keys file's section that holds the access token.");
            }
        }
    }
}
