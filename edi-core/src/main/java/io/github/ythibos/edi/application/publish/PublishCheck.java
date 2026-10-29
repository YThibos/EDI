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
import io.github.ythibos.edi.domain.delivery.SnapshotVersions;
import io.github.ythibos.edi.domain.publish.DenyList;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.publish.PersonalPaths;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RequestText;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import io.github.ythibos.edi.domain.publish.TextVerdict;

@Service
public class PublishCheck {

    private final EdiHome home;
    private final Git git;
    private final Gitleaks gitleaks;
    private final PublicationTargets targets;

    public PublishCheck(EdiHome home, Git git, Gitleaks gitleaks, PublicationTargets targets) {
        this.home = home;
        this.git = git;
        this.gitleaks = gitleaks;
        this.targets = targets;
    }

    public PublishVerdict checkPush(Path repository, RevisionRange outgoing, String remoteUrl) {
        OutgoingChanges changes = git.outgoingChanges(repository, outgoing);
        targets.requireRegistered(repository, remoteUrl, changes);
        return verdictOn(repository, outgoing, changes);
    }

    public TextVerdict checkText(Path repository, RequestText text) {
        return TextVerdict.of(text, denyList().sparing(targets.ownNamesOf(repository)), gitleaks.secretsIn(text));
    }

    public PublishVerdict check(Path repository, RevisionRange outgoing) {
        return verdictOn(repository, outgoing, git.outgoingChanges(repository, outgoing));
    }

    private PublishVerdict verdictOn(Path repository, RevisionRange outgoing, OutgoingChanges changes) {
        DenyList denyList = denyList();
        return PublishVerdict.of(
                        gitleaks.secretsIn(repository, outgoing),
                        denyList.sparing(targets.ownNamesOf(repository)).findIn(changes),
                        personalPathsOf(repository).findIn(changes),
                        SnapshotVersions.findIn(changes))
                .hidingPathsDeniedBy(denyList, changes);
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
