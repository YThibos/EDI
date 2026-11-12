package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.Adoption;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.testing.MachineFixture;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class RelaunchWorkerTest {

    private static final TaskReference LAMP = new TaskReference.ById(new TaskId("fit-lamp"));
    private static final TaskReference KITE = new TaskReference.ById(new TaskId("fly-kite"));

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker lamp;

    @BeforeEach
    void closeTheWindowOfAWorker() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        home.runtime().closeEveryWindow();
    }

    @Test
    void resumesTheRecordedSessionWhenItsTranscriptIsKept() {
        Launched relaunched = home.relaunchWorker().relaunch(LAMP, Actor.PRIMARY);

        Launch resumed = home.runtime().lastLaunch();
        assertThat(resumed.kind()).isEqualTo(LaunchKind.RESUME);
        assertThat(resumed.session()).isEqualTo(lamp.session());
        assertThat(resumed.login().name()).isEqualTo(LoginName.DEFAULT);
        assertThat(home.runtime().transcriptLookups()).extracting(Login::name).containsExactly(LoginName.DEFAULT);
        assertThat(relaunched.unresumed()).isNull();
    }

    @Test
    void startsAfreshWithAHandoverWhenTheRecordedSessionLeftNoTranscript() {
        home.runtime().leaveNoTranscriptOf(lamp.session());

        Launched relaunched = home.relaunchWorker().relaunch(LAMP, Actor.PRIMARY);

        Launch fresh = home.runtime().lastLaunch();
        Path handover = home.home().taskDirectory(lamp.id().task()).resolve("handover.md");
        String because = "its window closed and session " + lamp.session() + " left no transcript to resume";
        assertThat(fresh.kind()).isEqualTo(LaunchKind.FRESH);
        assertThat(fresh.session()).isNotEqualTo(lamp.session());
        assertThat(fresh.firstPrompt()).isEqualTo("⁣EDI_OP: fresh start after " + because + ": read the handover " + handover
                + " and carry on from where it leaves off");
        assertThat(handover).content().contains("fit-lamp#2 takes over from fit-lamp#1 in the same isolated copy, because " + because + ".");
        assertThat(relaunched.unresumed().session()).isEqualTo(lamp.session());
        assertThat(relaunched.worker().launch().kind()).isEqualTo(LaunchKind.FRESH);
    }

    @Test
    void resumesAWorkerOnTheLoginItWasLaunchedOnAndLooksForItsTranscriptThere() throws IOException {
        Login second = new Login(new LoginName("second"), directory.resolve("logins/second"));
        home = WorkerFixtures.homeWithHarnessOnTheLogin(directory.resolve("on-a-login"), "second", second.configDirectory());
        Worker kite = WorkerFixtures.dispatchedShip(home, "fly-kite", "HRN-5");
        home.runtime().closeEveryWindow();

        Launched relaunched = home.relaunchWorker().relaunch(KITE, Actor.PRIMARY);

        Launch resumed = home.runtime().lastLaunch();
        assertThat(home.workers().get(kite.id()).orElseThrow().launch().login()).isEqualTo(second.name());
        assertThat(resumed).extracting(Launch::kind, Launch::session, Launch::login).containsExactly(LaunchKind.RESUME, kite.session(), second);
        assertThat(home.runtime().transcriptLookups()).containsExactly(second);
        assertThat(home.runtime().readinessChecks().getLast()).isEqualTo(second);
        assertThat(home.workers().get(relaunched.worker().id()).orElseThrow().launch().login()).isEqualTo(second.name());
    }

    @Test
    void keepsAResumeOnTheRecordedLoginAfterItsProjectMovedAndStartsAFreshOneOnTheProjectsNewLogin() throws IOException {
        Login second = new Login(new LoginName("second"), directory.resolve("logins/second"));
        Login third = new Login(new LoginName("third"), directory.resolve("logins/third"));
        home = WorkerFixtures.homeWithHarnessOnTheLogin(directory.resolve("on-a-login"), "second", second.configDirectory());
        Worker kite = WorkerFixtures.dispatchedShip(home, "fly-kite", "HRN-5");
        WorkerFixtures.harnessMovedToTheLogin(home.withTheClaudeLogin("third", third.configDirectory()), "third");
        home.runtime().closeEveryWindow();

        home.relaunchWorker().relaunch(KITE, Actor.PRIMARY);
        Launch resumed = home.runtime().lastLaunch();
        home.runtime().closeEveryWindow();
        Launched fresh = home.relaunchWorker().relaunchFresh(KITE, Actor.PRIMARY);

        assertThat(resumed).extracting(Launch::kind, Launch::session, Launch::login).containsExactly(LaunchKind.RESUME, kite.session(), second);
        assertThat(home.runtime().lastLaunch()).extracting(Launch::kind, Launch::login).containsExactly(LaunchKind.FRESH, third);
        assertThat(home.runtime().lastLaunch().session()).isNotEqualTo(kite.session());
        assertThat(fresh.worker().launch()).extracting(WorkerLaunch::kind, WorkerLaunch::login).containsExactly(LaunchKind.FRESH, third.name());
    }

    @Test
    void startsAfreshOnTheProjectsNewLoginWhenTheRecordedLoginKeepsNoTranscriptOfTheSession() throws IOException {
        Login third = new Login(new LoginName("third"), directory.resolve("logins/third"));
        home = WorkerFixtures.homeWithHarnessOnTheLogin(directory.resolve("on-a-login"), "second", directory.resolve("logins/second"));
        Worker kite = WorkerFixtures.dispatchedShip(home, "fly-kite", "HRN-5");
        WorkerFixtures.harnessMovedToTheLogin(home.withTheClaudeLogin("third", third.configDirectory()), "third");
        home.runtime().closeEveryWindow();
        home.runtime().leaveNoTranscriptOf(kite.session());

        Launched relaunched = home.relaunchWorker().relaunch(KITE, Actor.PRIMARY);

        assertThat(home.runtime().transcriptLookups()).extracting(Login::name).containsExactly(new LoginName("second"));
        assertThat(home.runtime().lastLaunch()).extracting(Launch::kind, Launch::login).containsExactly(LaunchKind.FRESH, third);
        assertThat(relaunched.unresumed().session()).isEqualTo(kite.session());
    }

    @Test
    void refusesToResumeASessionWhoseLoginTheMachineProfileNoLongerNamesAndStartsItFreshOnTheCaptainsWord() throws IOException {
        home = WorkerFixtures.homeWithHarnessOnTheLogin(directory.resolve("on-a-login"), "second", directory.resolve("logins/second"));
        Worker kite = WorkerFixtures.dispatchedShip(home, "fly-kite", "HRN-5");
        WorkerFixtures.harnessMovedToTheLogin(home.on(MachineFixture.current()), "default");
        home.runtime().closeEveryWindow();
        int launches = home.runtime().launches().size();

        assertThatThrownBy(() -> home.relaunchWorker().relaunch(KITE, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage(kite.id() + "'s session ran on the Claude login second, which the machine profile no longer names, and a session resumes"
                        + " only on its own login; restore second under claude.logins to resume it, or start it fresh with edi worker relaunch fly-kite"
                        + " --fresh.");
        assertThat(home.runtime().launches()).hasSize(launches);

        home.relaunchWorker().relaunchFresh(KITE, Actor.PRIMARY);

        assertThat(home.runtime().lastLaunch()).extracting(Launch::kind, launch -> launch.login().name())
                .containsExactly(LaunchKind.FRESH, LoginName.DEFAULT);
    }

    @Test
    void resumesASessionAdoptedFromTheOldHarnessOnTheDefaultLoginWhicheverLoginItsProjectRunsOn() throws IOException {
        home = WorkerFixtures.homeWithHarnessOnTheLogin(directory.resolve("on-a-login"), "second", directory.resolve("logins/second"));
        UUID adopted = UUID.randomUUID();
        home.storing(List.of(WorkerFixtures.queuedShip("fly-kite", "harness", "HRN-5")));
        home.adoptions().save(new Adoption(new TaskId("fly-kite"), BranchName.parse("feature/HRN-5"), adopted, TestHome.START));

        home.dispatchTask().dispatch(KITE, new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), CallingSession.UNNAMED, Actor.PRIMARY);

        assertThat(home.runtime().lastLaunch()).extracting(Launch::kind, Launch::session, launch -> launch.login().name())
                .containsExactly(LaunchKind.RESUME, adopted, LoginName.DEFAULT);
    }
}
