package io.github.ythibos.edi.web.statusline;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.statusline.StatusLine;
import io.github.ythibos.edi.application.statusline.StatusLineQuery;
import io.github.ythibos.edi.web.access.Caller;
import tools.jackson.databind.JsonNode;

@RestController
class StatusLineController {

    private final StatusLineQuery statusLines;

    StatusLineController(StatusLineQuery statusLines) {
        this.statusLines = statusLines;
    }

    @PostMapping("/api/statusline")
    StatusLine line(@RequestBody JsonNode payload, Caller caller) {
        caller.requireTheHomeToken("edi statusline");
        return statusLines.lineFor(StatusLinePayload.sessionIn(payload), StatusLinePayload.workingDirectoryIn(payload));
    }
}
