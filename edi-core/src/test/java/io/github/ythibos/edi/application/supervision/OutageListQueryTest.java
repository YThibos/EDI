package io.github.ythibos.edi.application.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.landing.CommitsOnTheForge;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.Counted;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.NotVerified;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class OutageListQueryTest {

    private static final String FORGE_ADDRESS = "https://github.com/example-owner/harness.git";
    private static final String ANOTHER_ADDRESS = "https://github.com/example-owner/other.git";
    private static final String HARNESS_UNREGISTERED = """
            projects:
              - name: EDI
                tracker: { kind: edi, projectKey: EDI }
            """;

    @TempDir
    private Path directory;

    private TestHome home;
    private FixtureRepository copy;
    private String branch;

    @BeforeEach
    void aDeadWorkerWithTwoCommitsInItsCopy() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        Worker horn = WorkerFixtures.dispatchedShip(home, "tune-horn", "HRN-4");
        copy = FixtureRepository.at(home.home().worktreeOf(new ProjectName("harness"), horn.id().task()));
        copy.write("horn.txt", "brass\n").commit("HRN-4 Cast the horn");
        copy.write("horn.txt", "polished brass\n").commit("HRN-4 Polish the horn");
        branch = copy.run("symbolic-ref", "--short", "HEAD").strip();
        copy.run("remote", "set-url", "origin", FORGE_ADDRESS);
        theCopyFetchedTheForgeAt(home.clock().now());
        home.runtime().restartTheServer();
        home.judgeWorkers().judge();
    }

    @Test
    void namesTheCommitsTheForgeDoesNotHoldWhenOnlyTheValidationRepositoryDoes() throws IOException {
        pushedToTheValidationRepository();

        assertThat(unpushedCommitsOfTheHorn()).isInstanceOfSatisfying(Counted.class, counted -> {
            assertThat(counted.remote()).isEqualTo("origin");
            assertThat(counted.missing()).isEqualTo(2);
        });
    }

    @Test
    void countsNoneWhenTheForgeHoldsTheBranchAtItsHead() throws IOException {
        pushedToTheValidationRepository();
        theForgeHoldsTheHead();

        assertThat(unpushedCommitsOfTheHorn()).isInstanceOfSatisfying(Counted.class, counted -> {
            assertThat(counted.remote()).isEqualTo("origin");
            assertThat(counted.areAllOnTheForge()).isTrue();
        });
    }

    @Test
    void countsOnlyTheCommitsBeyondWhatTheForgeHolds() throws IOException {
        copy.run("update-ref", "refs/remotes/origin/" + branch, "HEAD~1");

        assertThat(unpushedCommitsOfTheHorn()).isInstanceOfSatisfying(Counted.class, counted -> assertThat(counted.missing()).isEqualTo(1));
    }

    @Test
    void isNotVerifiedWhenNoRemoteOfTheCopyPushesToTheRegisteredForge() throws IOException {
        copy.run("remote", "set-url", "origin", ANOTHER_ADDRESS);
        pushedToTheValidationRepository();

        assertThat(unpushedCommitsOfTheHorn()).isEqualTo(new NotVerified("no remote of its copy pushes to https://github.com/example-owner/harness"));
    }

    @Test
    void isNotVerifiedWhenTwoRemotesBothPushToTheRegisteredForge() throws IOException {
        copy.run("remote", "add", "mirror", FORGE_ADDRESS);

        assertThat(unpushedCommitsOfTheHorn()).isEqualTo(
                new NotVerified("its remotes mirror and origin cannot be told apart, as each pushes to https://github.com/example-owner/harness"));
    }

    @Test
    void isNotVerifiedWhenTheForgeRemoteFetchesFromTheForgeButPushesElsewhere() throws IOException {
        copy.run("remote", "set-url", "--push", "origin", ANOTHER_ADDRESS);

        assertThat(unpushedCommitsOfTheHorn()).isInstanceOf(NotVerified.class);
    }

    @Test
    void trustsTheCopysKnowledgeOfTheForgeWhileItsLastFetchIsRecent() {
        home.clock().advance(Duration.ofHours(1));

        assertThat(unpushedCommitsOfTheHorn()).isInstanceOfSatisfying(Counted.class, counted -> {
            assertThat(counted.sinceTheLastFetch()).contains(Duration.ofHours(1));
            assertThat(counted.mayBeOutOfDate()).isFalse();
        });
    }

    @Test
    void saysTheCopysKnowledgeOfTheForgeMayBeOutOfDateOnceItsLastFetchIsOld() {
        home.clock().advance(Duration.ofHours(25));

        assertThat(unpushedCommitsOfTheHorn()).isInstanceOfSatisfying(Counted.class, counted -> {
            assertThat(counted.sinceTheLastFetch()).contains(Duration.ofHours(25));
            assertThat(counted.mayBeOutOfDate()).isTrue();
        });
    }

    @Test
    void takesAFetchFromTheValidationRepositoryForNoFetchFromTheForge() throws IOException {
        pushedToTheValidationRepository();
        copy.run("fetch", "--quiet", "no-mistakes");

        assertThat(unpushedCommitsOfTheHorn()).isInstanceOfSatisfying(Counted.class, counted -> {
            assertThat(counted.sinceTheLastFetch()).isEmpty();
            assertThat(counted.mayBeOutOfDate()).isTrue();
        });
    }

    @Test
    void listsEveryEntryAsNotVerifiedWhenItsProjectIsNoLongerRegistered() throws IOException {
        home.registering(HARNESS_UNREGISTERED);

        assertThat(unpushedCommitsOfEveryEntry()).containsOnly(new NotVerified("harness is not a registered project"));
    }

    @Test
    void listsEveryEntryAsNotVerifiedWhenTheRegistryCannotBeRead() throws IOException {
        home.registering("projects: [\n");

        assertThat(unpushedCommitsOfEveryEntry()).containsOnly(new NotVerified("the project registry cannot be read"));
    }

    private void theCopyFetchedTheForgeAt(Instant fetched) throws IOException {
        Path record = Path.of(copy.run("rev-parse", "--path-format=absolute", "--git-path", "FETCH_HEAD").strip());
        Files.writeString(record, copy.run("rev-parse", "HEAD").strip() + "\t\tbranch '" + branch + "' of https://github.com/example-owner/harness\n");
        Files.setLastModifiedTime(record, FileTime.from(fetched));
    }

    private CommitsOnTheForge unpushedCommitsOfTheHorn() {
        return home.outageList().list().entries().stream()
                .filter(entry -> entry.task().id().toString().equals("tune-horn"))
                .findFirst().orElseThrow()
                .unpushed().commits();
    }

    private List<CommitsOnTheForge> unpushedCommitsOfEveryEntry() {
        List<OutageList.Entry> entries = home.outageList().list().entries();
        assertThat(entries).hasSize(2);
        return entries.stream().map(entry -> entry.unpushed().commits()).toList();
    }

    private void pushedToTheValidationRepository() throws IOException {
        Path validation = Files.createDirectories(directory.resolve("validation.git"));
        FixtureRepository.at(validation).run("init", "--quiet", "--bare");
        copy.run("remote", "add", "no-mistakes", validation.toString());
        copy.run("push", "--quiet", "--no-verify", "no-mistakes", "HEAD:refs/heads/" + branch);
    }

    private void theForgeHoldsTheHead() throws IOException {
        copy.run("update-ref", "refs/remotes/origin/" + branch, "HEAD");
    }
}
