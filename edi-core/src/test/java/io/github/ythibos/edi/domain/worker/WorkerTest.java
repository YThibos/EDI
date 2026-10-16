package io.github.ythibos.edi.domain.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;

class WorkerTest {

    private static final Instant LAUNCHED = Instant.parse("2026-10-16T09:00:00Z");
    private static final WorkerWindow WINDOW = new WorkerWindow("edi", 7, LAUNCHED, "@1", 8);

    @Test
    void isBusyBetweenAPromptAndItsStopAndIdleAfterwards() {
        Worker worker = launched(CategoryId.IMPLEMENTATION);

        assertThat(worker.activity()).isEqualTo(WorkerActivity.STARTING);
        assertThat(worker.started(LAUNCHED).activity()).isEqualTo(WorkerActivity.IDLE);
        assertThat(worker.promptSubmitted(LAUNCHED.plusSeconds(3)).activity()).isEqualTo(WorkerActivity.BUSY);
        assertThat(worker.promptSubmitted(LAUNCHED.plusSeconds(3)).stopped().isIdle()).isTrue();
        assertThat(worker.promptSubmitted(LAUNCHED.plusSeconds(3)).briefedAt()).isEqualTo(LAUNCHED.plusSeconds(3));
    }

    @Test
    void acceptsADoneAtOnceUnlessItIsMechanical() {
        StatusReport done = new StatusReport(StatusState.DONE, null, null, "Fitted.");

        assertThat(launched(CategoryId.IMPLEMENTATION).reported(done, LAUNCHED).doneCheck()).isEqualTo(DoneCheck.ACCEPTED);
        Worker mechanical = launched(CategoryId.MECHANICAL).reported(done, LAUNCHED);
        assertThat(mechanical.awaitsItsDoneCheck()).isTrue();
        assertThat(mechanical.doneChecked(false).doneCheck()).isEqualTo(DoneCheck.REFUSED);
        assertThat(mechanical.reported(new StatusReport(StatusState.WORKING, null, null, "Again."), LAUNCHED).doneCheck()).isNull();
        assertThatThrownBy(() -> launched(CategoryId.IMPLEMENTATION).doneChecked(true)).isInstanceOf(Refusal.class);
    }

    @Test
    void missesItsLaunchOnlyWithoutAnyHookAfterThePatience() {
        Duration patience = Duration.ofMinutes(2);

        assertThat(launched(CategoryId.IMPLEMENTATION).missedItsLaunch(LAUNCHED.plus(patience), patience)).isTrue();
        assertThat(launched(CategoryId.IMPLEMENTATION).missedItsLaunch(LAUNCHED.plus(patience).minusMillis(1), patience)).isFalse();
        assertThat(launched(CategoryId.IMPLEMENTATION).started(LAUNCHED).missedItsLaunch(LAUNCHED.plus(patience), patience)).isFalse();
        assertThat(launched(CategoryId.IMPLEMENTATION).launchFailed(LAUNCHED).missedItsLaunch(LAUNCHED.plus(patience), patience)).isFalse();
        assertThatThrownBy(() -> launched(CategoryId.IMPLEMENTATION).retired(LAUNCHED).reported(new StatusReport(StatusState.DONE, null, null, "x"), LAUNCHED))
                .isInstanceOf(Refusal.class).hasMessageContaining("was retired");
    }

    private static Worker launched(CategoryId category) {
        return Worker.launched(WorkerId.first(new TaskId("fit-lamp")), UUID.randomUUID(), category, new Route(new ModelId("claude-haiku-5-5"), null),
                WINDOW, LAUNCHED);
    }
}
