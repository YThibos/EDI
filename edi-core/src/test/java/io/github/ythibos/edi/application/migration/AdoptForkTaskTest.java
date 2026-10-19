package io.github.ythibos.edi.application.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.testing.ExampleHome;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class AdoptForkTaskTest {

    private static final Path OLD_HOME_FIXTURE = Path.of("src/test/resources/fork-home");
    private static final String REGISTRY = """
            projects:
              - name: lighthouse
                tracker: { kind: edi, projectKey: LHT }
                base: main
            """;

    @TempDir
    private Path directory;

    @Test
    void takesOverOnlyOpenWorkOnTheProjectsEdiKeeps() throws IOException {
        Path oldHome = copyOfTheOldHome();
        AdoptForkTask adoption = TestHome.in(directory.resolve("edi-home")).registering(REGISTRY).adoptForkTask();
        Path copy = directory.resolve("pool/1/lighthouse");

        assertThatThrownBy(() -> adoption.adopt(new TaskId("flag-pole"), oldHome, BranchName.parse("feature/LHT-1"), copy, null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("flag-pole is done in the old home, and only open work is taken over.");
        assertThatThrownBy(() -> adoption.adopt(new TaskId("old-harness-polish"), oldHome, BranchName.parse("feature/LHT-2"), copy, null,
                Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("improves the old harness itself");
        assertThatThrownBy(() -> adoption.adopt(new TaskId("ferry-timetable"), oldHome, BranchName.parse("feature/EDI-8"), copy, null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("ferry-timetable books its work on EDI-7, but feature/EDI-8 carries EDI-8.");
        assertThatThrownBy(() -> adoption.adopt(new TaskId("relay-mast"), oldHome, BranchName.parse("feature/LHT-3"), copy, null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("is not a task EDI imports from " + oldHome + ": it is not imported");
        assertThatThrownBy(() -> adoption.adopt(new TaskId("harbour-wall"), oldHome, BranchName.parse("chore/LHT-4"), copy, null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("harbour is not a registered project; add it to the project registry first.");
    }

    @Test
    void refusesASessionWithoutATranscriptAndStartsAFreshSessionWithoutOne() throws IOException {
        Path oldHome = copyOfTheOldHome();
        TestHome home = TestHome.in(directory.resolve("edi-home")).registering(REGISTRY)
                .writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json"));
        FixtureRepository upstream = FixtureRepository.initialise(Files.createDirectories(directory.resolve("upstream/lighthouse")))
                .write("README.md", "lamp\n").commit("Start");
        upstream.clonedInto(home.home().root().resolve("projects/lighthouse"));
        Path oldCopy = directory.resolve("pool/1/lighthouse");
        upstream.clonedInto(oldHome.resolve("projects/lighthouse")).run("worktree", "add", "--quiet", "-b", "feature/LHT-3", oldCopy.toString());
        TaskId lantern = new TaskId("lantern-rework");
        BranchName branch = BranchName.parse("feature/LHT-3");

        assertThatThrownBy(() -> home.adoptForkTask().adopt(lantern, oldHome, branch, oldCopy, UUID.randomUUID(), Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("adopt without --session to start the next worker afresh");
        assertThat(home.tasks().ids()).isEmpty();

        assertThat(home.adoptForkTask().adopt(lantern, oldHome, branch, oldCopy, null, Actor.PRIMARY).resumesASession()).isFalse();
        home.dispatchTask().dispatch(new TaskReference.ById(lantern), new DispatchRequest(CategoryId.IMPLEMENTATION, "Carry on.", List.of(), null,
                false), Actor.PRIMARY);

        Launch launch = home.runtime().launches().getLast();
        assertThat(launch.kind()).isEqualTo(LaunchKind.DISPATCH);
        assertThat(launch.worktree()).isEqualTo(home.home().worktreeOf(home.tasks().get(lantern).intake().projects().getFirst(), lantern));
    }

    private Path copyOfTheOldHome() throws IOException {
        Path copy = directory.resolve("old-home");
        try (Stream<Path> files = Files.walk(OLD_HOME_FIXTURE)) {
            for (Path file : files.toList()) {
                Path target = copy.resolve(OLD_HOME_FIXTURE.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        return copy;
    }
}
