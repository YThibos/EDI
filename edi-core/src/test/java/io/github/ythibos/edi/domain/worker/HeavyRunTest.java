package io.github.ythibos.edi.domain.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.HeavyRuns.WorkerRun;

class HeavyRunTest {

    private static final String MAVEN_LAUNCHER = "org.codehaus.plexus.classworlds.launcher.Launcher";
    private static final WorkerRun LAMP_BUILDING = new WorkerRun(WorkerId.first(new TaskId("fit-lamp")), HeavyRun.NATIVE_BUILD);
    private static final WorkerRun HORN_TESTING = new WorkerRun(WorkerId.first(new TaskId("tune-horn")), HeavyRun.TEST_RUN);

    @Test
    void readsAMavenLaunchWithTheNativeProfileAsANativeBuild() {
        assertThat(HeavyRun.of(maven("-B", "-Pnative,quick-build", "verify"))).contains(HeavyRun.NATIVE_BUILD);
        assertThat(HeavyRun.of(maven("-P", "quick-build,native", "package"))).contains(HeavyRun.NATIVE_BUILD);
    }

    @Test
    void readsAMavenLaunchThroughATestingPhaseAsATestRun() {
        assertThat(HeavyRun.of(maven("-B", "-ntp", "verify"))).contains(HeavyRun.TEST_RUN);
        assertThat(HeavyRun.of(maven("test", "-Pfast"))).contains(HeavyRun.TEST_RUN);
        assertThat(HeavyRun.of(maven("-Pnative-tools", "install"))).contains(HeavyRun.TEST_RUN);
    }

    @Test
    void leavesLightMavenLaunchesAndOtherProgramsAlone() {
        assertThat(HeavyRun.of(maven("-q", "compile"))).isEmpty();
        assertThat(HeavyRun.of(maven("-P"))).isEmpty();
        assertThat(HeavyRun.of(List.of("./mvnw", "-Pnative", "verify"))).isEmpty();
        assertThat(HeavyRun.of(List.of())).isEmpty();
    }

    @Test
    void namesTheRunsThatStartedSinceTheLastLookAndWhatRunsBesideThem() {
        HeavyRuns alone = new HeavyRuns(List.of(LAMP_BUILDING));
        HeavyRuns together = new HeavyRuns(List.of(LAMP_BUILDING, HORN_TESTING));

        assertThat(alone.runAtOnce()).isFalse();
        assertThat(together.runAtOnce()).isTrue();
        assertThat(together.startedSince(alone)).containsExactly(HORN_TESTING);
        assertThat(together.startedSince(together)).isEmpty();
        assertThat(together.beside(HORN_TESTING)).containsExactly(LAMP_BUILDING);
    }

    private static List<String> maven(String... arguments) {
        return Stream.concat(Stream.of("-classpath", "/opt/maven/boot/plexus-classworlds-2.9.0.jar", MAVEN_LAUNCHER), Stream.of(arguments)).toList();
    }
}
