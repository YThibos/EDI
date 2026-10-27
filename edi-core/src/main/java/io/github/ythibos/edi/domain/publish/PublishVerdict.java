package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.stream.Stream;

public record PublishVerdict(List<PublishFinding> findings) {

    public PublishVerdict {
        findings = List.copyOf(findings);
    }

    public static PublishVerdict of(List<PublishFinding> secrets, List<PublishFinding> deniedTerms, List<PublishFinding> personalPaths,
            List<PublishFinding> snapshotVersions) {
        return new PublishVerdict(Stream.of(secrets, deniedTerms, personalPaths, snapshotVersions).flatMap(List::stream).toList());
    }

    public PublishVerdict hidingPathsDeniedBy(DenyList denyList, OutgoingChanges changes) {
        return new PublishVerdict(findings.stream().map(finding -> finding.hidingPathsDeniedBy(denyList, changes)).toList());
    }

    public boolean allowsPush() {
        return findings.isEmpty();
    }

    public String conclusion(RevisionRange outgoing) {
        return allowsPush()
                ? "Clear: no secret, no personal material and no -SNAPSHOT version in " + outgoing
                : "Refused: the outgoing commits carry a secret, personal material or a -SNAPSHOT version.";
    }
}
