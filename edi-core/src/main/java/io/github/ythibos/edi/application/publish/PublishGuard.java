package io.github.ythibos.edi.application.publish;

import java.nio.file.Path;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.gitleaks.Gitleaks;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import tools.jackson.databind.json.JsonMapper;

public final class PublishGuard {

    private static final int REFUSED = 1;

    private PublishGuard() {
    }

    public static void main(String... arguments) {
        Path repository = Path.of(arguments[0]);
        RevisionRange outgoing = new RevisionRange(arguments[1]);
        PublishVerdict verdict = publishCheck(Path.of(arguments[2])).check(repository, outgoing);
        report(verdict, outgoing);
        if (!verdict.allowsPush()) {
            System.exit(REFUSED);
        }
    }

    static PublishCheck publishCheck(Path ediHome) {
        ExternalPrograms programs = new ExternalPrograms();
        return new PublishCheck(new EdiHome(ediHome), new Git(programs), new Gitleaks(programs, new JsonMapper()));
    }

    private static void report(PublishVerdict verdict, RevisionRange outgoing) {
        verdict.findings().forEach(System.out::println);
        System.out.println(verdict.conclusion(outgoing));
    }
}
