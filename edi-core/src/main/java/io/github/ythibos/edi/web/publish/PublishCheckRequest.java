package io.github.ythibos.edi.web.publish;

import static io.github.ythibos.edi.web.RequestValues.required;

import java.nio.file.Path;

import io.github.ythibos.edi.domain.publish.RevisionRange;

record PublishCheckRequest(String repository, String range, String remoteUrl) {

    Path repositoryPath() {
        Path path = Path.of(required(repository, "repository"));
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Name the repository by its absolute path, not " + repository + ".");
        }
        return path.normalize();
    }

    RevisionRange outgoing() {
        return new RevisionRange(required(range, "range"));
    }

    boolean namesThePushTarget() {
        return remoteUrl != null && !remoteUrl.isBlank();
    }
}
