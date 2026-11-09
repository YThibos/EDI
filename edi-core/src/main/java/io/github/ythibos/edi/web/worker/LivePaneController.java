package io.github.ythibos.edi.web.worker;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.github.ythibos.edi.application.worker.MirrorWorker;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class LivePaneController {

    private final MirrorWorker mirrors;
    private final LivePanes panes;

    LivePaneController(MirrorWorker mirrors, LivePanes panes) {
        this.mirrors = mirrors;
        this.panes = panes;
    }

    @GetMapping(value = "/api/tasks/{reference}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter live(@PathVariable String reference, Caller caller) {
        caller.requireThePrimaryOrTheCaptain("A worker's live pane");
        return panes.watch(mirrors.servingWorkerOf(TaskReference.parse(reference)));
    }
}
