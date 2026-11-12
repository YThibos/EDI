package io.github.ythibos.edi.application.publish;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.forge.ForgeCalls;
import io.github.ythibos.edi.adapter.forge.GitHubForge;
import io.github.ythibos.edi.adapter.forge.GitLabForge;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.gitleaks.Gitleaks;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.application.delivery.Forges;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import tools.jackson.databind.json.JsonMapper;

public final class PublishGuard {

    private static final int REFUSED = 1;
    private static final int REMOTE_URL = 3;
    private static final int EXCLUSIONS = 4;

    private PublishGuard() {
    }

    public static void main(String... arguments) {
        RevisionRange outgoing = outgoingOf(arguments);
        try {
            PublishVerdict verdict = verdictOn(arguments, outgoing);
            report(verdict, outgoing);
            if (!verdict.allowsPush()) {
                System.exit(REFUSED);
            }
        } catch (Refusal refused) {
            System.out.println(refused.getMessage());
            System.exit(REFUSED);
        }
    }

    private static RevisionRange outgoingOf(String[] arguments) {
        return new RevisionRange(arguments[1], Arrays.stream(arguments).skip(EXCLUSIONS).toList());
    }

    private static PublishVerdict verdictOn(String[] arguments, RevisionRange outgoing) {
        Path repository = Path.of(arguments[0]);
        PublishCheck publishCheck = publishCheck(Path.of(arguments[2]));
        return arguments.length > REMOTE_URL
                ? publishCheck.checkPush(repository, outgoing, arguments[REMOTE_URL])
                : publishCheck.check(repository, outgoing);
    }

    static PublishCheck publishCheck(Path ediHome) {
        ExternalPrograms programs = new ExternalPrograms();
        JsonMapper json = new JsonMapper();
        EdiHome home = new EdiHome(ediHome);
        Git git = new Git(programs);
        ForgeCalls calls = new ForgeCalls(programs, "", "");
        Forges forges = new Forges(List.of(new GitHubForge(calls, json), new GitLabForge(calls, json)));
        MachineProfile machine = new MachineProfile(home);
        PublicationTargets targets = new PublicationTargets(home, git, new ProjectRegistry(home, machine), machine, forges);
        return new PublishCheck(home, git, new Gitleaks(programs, json), targets);
    }

    private static void report(PublishVerdict verdict, RevisionRange outgoing) {
        verdict.findings().forEach(System.out::println);
        System.out.println(verdict.conclusion(outgoing));
    }
}
