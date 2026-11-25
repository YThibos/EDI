package io.github.ythibos.edi.application.delivery;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.registry.UnreadableRegistry;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.Counted;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.NotVerified;
import io.github.ythibos.edi.domain.project.ProjectName;

@Component
public class PushedWorkCheck {

    private final ProjectRegistry registry;
    private final Git git;
    private final Clock clock;

    public PushedWorkCheck(ProjectRegistry registry, Git git, Clock clock) {
        this.registry = registry;
        this.git = git;
        this.clock = clock;
    }

    public CommitsOnTheForge commitsOf(ProjectName project, Path copy) {
        try {
            return commitsOn(registry.entryOf(project).forge(), project, copy);
        } catch (Refusal unregistered) {
            return new NotVerified(project.value() + " is not a registered project");
        } catch (UnreadableRegistry unreadable) {
            return new NotVerified("the project registry cannot be read");
        }
    }

    private CommitsOnTheForge commitsOn(RegisteredForge forge, ProjectName project, Path copy) {
        if (forge == null) {
            return new NotVerified(project.value() + " registers no forge");
        }
        List<String> forgeRemotes = forgeRemotesOf(copy, forge);
        return forgeRemotes.size() == 1 ? countedOn(copy, forgeRemotes.getFirst(), forge) : notVerified(forgeRemotes, forge);
    }

    private List<String> forgeRemotesOf(Path copy, RegisteredForge forge) {
        return git.remotesOf(copy).stream().filter(remote -> pushesTo(copy, remote, forge)).toList();
    }

    private boolean pushesTo(Path copy, String remote, RegisteredForge forge) {
        return git.fetchUrlOf(copy, remote).filter(forge::isServedAt).isPresent() && forge.isServedAt(git.pushUrlOf(copy, remote));
    }

    private Counted countedOn(Path copy, String remote, RegisteredForge forge) {
        Optional<Duration> sinceTheLastFetch = git.lastFetchFrom(copy, forge::isServedAt).map(fetched -> Duration.between(fetched, clock.now()));
        return new Counted(remote, git.commitsNotOnRemote(copy, remote), sinceTheLastFetch);
    }

    private static NotVerified notVerified(List<String> forgeRemotes, RegisteredForge forge) {
        return forgeRemotes.isEmpty() ? noRemotePushesTo(forge) : indistinguishable(forgeRemotes, forge);
    }

    private static NotVerified noRemotePushesTo(RegisteredForge forge) {
        return new NotVerified("no remote of its copy pushes to " + forge.repository());
    }

    private static NotVerified indistinguishable(List<String> forgeRemotes, RegisteredForge forge) {
        return new NotVerified("its remotes " + String.join(" and ", forgeRemotes) + " cannot be told apart, as each pushes to " + forge.repository());
    }
}
