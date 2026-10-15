package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.stream.Stream;

public record PublishVerdict(List<PublishFinding> findings) {

    public PublishVerdict {
        findings = List.copyOf(findings);
    }

    public static PublishVerdict of(List<PublishFinding> secrets, List<PublishFinding> deniedTerms, List<PublishFinding> personalPaths) {
        return new PublishVerdict(Stream.of(secrets, deniedTerms, personalPaths).flatMap(List::stream).toList());
    }

    public PublishVerdict hidingPathsDeniedBy(DenyList denyList, OutgoingChanges changes) {
        return new PublishVerdict(findings.stream().map(finding -> finding.hidingPathsDeniedBy(denyList, changes)).toList());
    }

    public boolean allowsPush() {
        return findings.isEmpty();
    }

    public String conclusion(RevisionRange outgoing) {
        return allowsPush()
                ? "Clear: no secret and no personal material in " + outgoing
                : "Refused: the outgoing commits carry a secret or personal material.";
    }
}
