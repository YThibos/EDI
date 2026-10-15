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

    public boolean allowsPush() {
        return findings.isEmpty();
    }
}
