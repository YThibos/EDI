package io.github.ythibos.edi.application.delivery;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.Counted;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.NotVerified;

@Component
public class PushedWorkCheck {

    private final Git git;
    private final Clock clock;

    public PushedWorkCheck(Git git, Clock clock) {
        this.git = git;
        this.clock = clock;
    }

    public CommitsOnTheForge commitsOf(ProjectEntry project, Path copy) {
        RegisteredForge forge = project.forge();
        if (forge == null) {
            return new NotVerified(project.project().name() + " registers no forge");
        }
        List<String> forgeRemotes = git.remotesOf(copy).stream().filter(remote -> pushesTo(copy, remote, forge)).toList();
        return forgeRemotes.size() == 1
                ? countedOn(copy, forgeRemotes.getFirst())
                : new NotVerified(forgeRemotes.isEmpty()
                        ? "no remote of its copy pushes to " + forge.repository()
                        : "its remotes " + String.join(" and ", forgeRemotes) + " cannot be told apart, as each pushes to " + forge.repository());
    }

    private boolean pushesTo(Path copy, String remote, RegisteredForge forge) {
        return git.fetchUrlOf(copy, remote).filter(forge::isServedAt).isPresent() && forge.isServedAt(git.pushUrlOf(copy, remote));
    }

    private Counted countedOn(Path copy, String remote) {
        Optional<Duration> sinceTheLastFetch = git.lastFetchOf(copy).map(fetched -> Duration.between(fetched, clock.now()));
        return new Counted(remote, git.commitsNotOnRemote(copy, remote), sinceTheLastFetch);
    }
}
