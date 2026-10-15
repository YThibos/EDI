package io.github.ythibos.edi.application.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.gitleaks.Gitleaks;
import io.github.ythibos.edi.domain.publish.DenyList;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.publish.PersonalPaths;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;

@Service
public class PublishCheck {

    private final EdiHome home;
    private final Git git;
    private final Gitleaks gitleaks;

    public PublishCheck(EdiHome home, Git git, Gitleaks gitleaks) {
        this.home = home;
        this.git = git;
        this.gitleaks = gitleaks;
    }

    public PublishVerdict check(Path repository, RevisionRange outgoing) {
        OutgoingChanges changes = git.outgoingChanges(repository, outgoing);
        return PublishVerdict.of(
                gitleaks.secretsIn(repository, outgoing),
                denyList().findIn(changes),
                personalPathsOf(repository).findIn(changes));
    }

    private DenyList denyList() {
        try {
            return DenyList.parse(Files.readString(home.publishDenyList()));
        } catch (NoSuchFileException absent) {
            return DenyList.empty();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private PersonalPaths personalPathsOf(Path repository) {
        Path topLevel = git.topLevel(repository);
        Path ediHome = canonical(home.root());
        PersonalPaths personalLayer = PersonalPaths.personalLayer();
        return ediHome.startsWith(topLevel)
                ? personalLayer.andEdiHomeAt(topLevel.relativize(ediHome) + "/")
                : personalLayer;
    }

    private static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (NoSuchFileException absent) {
            return path.toAbsolutePath().normalize();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
