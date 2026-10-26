package io.github.ythibos.edi.application.delivery;

import java.io.IOException;
import java.util.List;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

final class DeliveryFixtures {

    static final String PUSH_URL = "https://github.com/example-owner/harness.git";

    private DeliveryFixtures() {
    }

    static DispatchOutcome dispatched(TestHome home, Task task) {
        home.storing(List.of(task));
        return home.dispatchTask().dispatch(new TaskReference.ById(task.id()), new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null),
                CallingSession.UNNAMED, Actor.PRIMARY);
    }

    static FixtureRepository committedIn(DispatchOutcome dispatched, String file, String content, String subject) throws IOException {
        return FixtureRepository.at(dispatched.launched().worktree()).write(file, content).commit(subject);
    }

    static Worker deliveredAsADraft(TestHome home, DispatchOutcome dispatched) throws IOException {
        Worker worker = dispatched.launched().worker();
        WorkerFixtures.opensTheDraftOf(home, worker);
        return home.recordStatus().report(worker.id(), StatusState.DONE, null, null, "Delivered.");
    }
}
