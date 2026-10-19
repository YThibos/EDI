package io.github.ythibos.edi.web.lifecycle;

import io.github.ythibos.edi.application.lifecycle.CoreStop;

record StopRequest(Boolean forceStopWorkers, Boolean restart) {

    CoreStop stop() {
        if (Boolean.TRUE.equals(restart)) {
            return CoreStop.RESTART;
        }
        return Boolean.TRUE.equals(forceStopWorkers) ? CoreStop.WITH_ITS_WORKERS : CoreStop.WHEN_NO_WORKER_RUNS;
    }
}
