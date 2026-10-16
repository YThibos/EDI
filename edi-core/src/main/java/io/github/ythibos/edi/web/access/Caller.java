package io.github.ythibos.edi.web.access;

import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.worker.WorkerId;

public sealed interface Caller {

    String ATTRIBUTE = Caller.class.getName();

    default Actor actor() {
        return switch (this) {
            case Primary _ -> Actor.PRIMARY;
            case WorkerCaller caller -> Actor.worker(caller.worker());
        };
    }

    default void requirePrimary(String command) {
        if (this instanceof WorkerCaller) {
            throw new NotPermitted(command + " is the primary's to run; a worker reports through edi status and reads edi inbox.");
        }
    }

    default WorkerId requireWorker(String command) {
        return switch (this) {
            case WorkerCaller caller -> caller.worker();
            case Primary _ -> throw new NotPermitted(command + " is a worker's command: it runs under the token EDI gives a worker at launch.");
        };
    }

    record Primary() implements Caller {
    }

    record WorkerCaller(WorkerId worker) implements Caller {
    }
}
