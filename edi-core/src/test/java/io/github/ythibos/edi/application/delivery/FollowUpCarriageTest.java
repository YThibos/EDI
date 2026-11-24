package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class FollowUpCarriageTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;
    private FixtureRepository upstream;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        upstream = FixtureRepository.at(directory.resolve("upstream/harness"));
    }

    @Test
    void discardsTheCommitsAfterTheMergedHeadOnTheFollowUpAnswerOnlyOnceAFollowUpTasksBranchHoldsThemOnTheForge() throws IOException {
        FixtureRepository copy = mergedWithALateCommit();
        String late = copy.head();
        CallingSession session = theCaptainChose(new OptionKey('A'));
        home.clock().advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> home.discardWork().discard(new TaskReference.ById(LAMP), session, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp's work is not discarded yet: the captain chose to deliver " + late.substring(0, 12) + " through a follow-up task,"
                        + " and no task dispatched since the decision has pushed its branch to the forge, by patch or by content."
                        + " Push the follow-up task's branch first.");

        FixtureRepository followUp = FixtureRepository.at(
                DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("carry-cap", "harness", "HRN-4")).launched().worktree());
        upstream.run("fetch", "--quiet", followUp.root().toString(), "feature/HRN-4:feature/HRN-4");
        assertThatThrownBy(() -> home.discardWork().discard(new TaskReference.ById(LAMP), session, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessageContaining("neither origin/feature/HRN-4 holds them");
        assertThat(copy.root()).isDirectory();

        followUp.run("cherry-pick", late);
        upstream.run("fetch", "--quiet", followUp.root().toString(), "feature/HRN-4:feature/HRN-4");
        DiscardedWork discarded = home.discardWork().discard(new TaskReference.ById(LAMP), session, Actor.PRIMARY);

        assertThat(discarded.discarded()).containsExactly("commit " + late.substring(0, 12) + " \"HRN-3 Fit the cap\" is not in origin/main");
        assertThat(copy.root()).doesNotExist();
    }

    @Test
    void discardsTheCommitsAfterTheMergedHeadAtOnceWhenTheCaptainChoseToDiscardThem() throws IOException {
        FixtureRepository copy = mergedWithALateCommit();
        CallingSession session = theCaptainChose(new OptionKey('B'));

        home.discardWork().discard(new TaskReference.ById(LAMP), session, Actor.PRIMARY);

        assertThat(copy.root()).doesNotExist();
    }

    private FixtureRepository mergedWithALateCommit() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        upstream.run("fetch", "--quiet", copy.root().toString(), "HEAD");
        upstream.run("merge", "--quiet", "--squash", "FETCH_HEAD");
        String landed = upstream.commit("Fit the lamp, squashed").head();
        copy.write("cap.txt", "brass\n").commit("HRN-3 Fit the cap");
        home.forge().merges(home.deliveries().of(LAMP).orElseThrow().address(), landed);
        home.followMerges().watchTheOpenDrafts();
        home.runtime().closeEveryWindow();
        return copy;
    }

    private CallingSession theCaptainChose(OptionKey option) {
        Decision followUp = home.decisions().ofTask(LAMP).orElseThrow();
        String verbatim = followUp.number() + " " + option;
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(session, verbatim);
        home.answerDecision().answer(new TaskReference.ById(LAMP), new GivenAnswer(verbatim, option, "", AnswerChannel.CHAT), Actor.PRIMARY);
        return new CallingSession(session);
    }
}
