package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.stream.Stream;

public record PublishVerdict(List<PublishFinding> findings, VocabularyCheck vocabularyCheck) {

    public PublishVerdict {
        findings = List.copyOf(findings);
    }

    public static PublishVerdict of(VocabularyCheck vocabularyCheck, List<PublishFinding> secrets, List<PublishFinding> deniedTerms,
            List<PublishFinding> personalPaths, List<PublishFinding> snapshotVersions, List<PublishFinding> ediVocabulary) {
        return new PublishVerdict(Stream.of(secrets, deniedTerms, personalPaths, snapshotVersions, ediVocabulary).flatMap(List::stream).toList(),
                vocabularyCheck);
    }

    public PublishVerdict hidingPathsDeniedBy(DenyList denyList, OutgoingChanges changes) {
        return new PublishVerdict(findings.stream().map(finding -> finding.hidingPathsDeniedBy(denyList, changes)).toList(), vocabularyCheck);
    }

    public boolean allowsPush() {
        return findings.isEmpty();
    }

    public String conclusion(RevisionRange outgoing) {
        return allowsPush() ? clearOf(outgoing) : refusal();
    }

    private String clearOf(RevisionRange outgoing) {
        return switch (vocabularyCheck) {
            case CHECKED -> "Clear: no secret, no personal material, no -SNAPSHOT version and no internal vocabulary in " + outgoing;
            case SPARED -> "Clear: no secret, no personal material and no -SNAPSHOT version in " + outgoing;
        };
    }

    private String refusal() {
        return switch (vocabularyCheck) {
            case CHECKED -> "Refused: the outgoing commits carry a secret, personal material, a -SNAPSHOT version or internal vocabulary.";
            case SPARED -> "Refused: the outgoing commits carry a secret, personal material or a -SNAPSHOT version.";
        };
    }
}
